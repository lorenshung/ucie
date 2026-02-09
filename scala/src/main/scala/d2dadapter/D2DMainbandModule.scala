package edu.berkeley.cs.ucie.digital.d2dadapter

import chisel3._
//import chisel3.util._
//import chisel3.experimental._

import interfaces._
import sideband._

// Spec Reference: Section 6.2.1 (Mainband Data Path Interface)
class D2DMainbandModuleIO(val fdiParams: FdiParams, val rdiParams: RdiParams, val sbParams: SidebandParams) extends Bundle{
    // Spec Reference: Section 6.2.1.1 (FDI to D2D Interface)
    //protocol to d2d
    val fdi_lp_irdy = Input(Bool())
    val fdi_lp_valid = Input(Bool())
    val fdi_lp_data = Input(Bits((8 * fdiParams.width).W))
    val fdi_lp_stream = Input(new ProtoStream())
    val fdi_pl_trdy = Output(Bool())
    
    // Spec Reference: Section 6.2.1.2 (D2D to FDI Interface)
    // d2d to protocol
    val fdi_pl_valid = Output(Bool())
    val fdi_pl_data = Output(Bits((8 * fdiParams.width).W))
    val fdi_pl_stream = Output(new ProtoStream)
    
    // Spec Reference: Section 6.2.1.3 (D2D to RDI Interface)
    // d2d to physical
    val rdi_lp_irdy = Output(Bool())
    val rdi_lp_valid = Output(Bool())
    val rdi_lp_data = Output(Bits((8 * rdiParams.width).W))
    val rdi_pl_trdy = Input(Bool())
    
    // Spec Reference: Section 6.2.1.4 (RDI to D2D Interface)
    // physical to d2d
    val rdi_pl_valid = Input(Bool())
    val rdi_pl_data = Input(Bits((8 * rdiParams.width).W))

    // Spec Reference: Section 8.1.3 (Link State Input)
    val d2d_state = Input(PhyState())

    // Spec Reference: Section 8.4.4 (Mainband Stall Control)
    val mainband_stallreq = Input(Bool())// 
    val mainband_stalldone = Output(Bool())// complete stall

    // Spec Reference: Section 9.2.1 (Parity Data Interface - Transmit)
    val snd_data = Output(Bits((8 * fdiParams.width).W))
    val snd_data_vld = Output(Bool())
    val rcv_data = Output(Bits((8 * rdiParams.width).W))
    val rcv_data_vld = Output(Bool())
    val parity_insert = Input(Bool())// need to send parity
    val parity_data = Input(Bits((8 * fdiParams.width).W))// the data needed to be sent first as parity
    val parity_rdy = Output(Bool()) // indicating that the parity data is sent, next can come in
    val parity_check = Input(Bool())// mean that the next data from RDI does not need to send to FDI
}

// Spec Reference: Section 6.2.1 (Mainband Data Path Module)
class D2DMainbandModule(val fdiParams: FdiParams, val rdiParams: RdiParams, val sbParams: SidebandParams) extends Module{
    val io = IO(new D2DMainbandModuleIO(fdiParams, rdiParams, sbParams))

    // Spec Reference: Section 6.2.1.5 (Data Buffering for Flow Control)
    val data_buff_snt_reg = Reg(Bits((8 * fdiParams.width).W))
    val data_buff_snt_fill_reg = RegInit(false.B)
    val data_buff_rcv_reg = Reg(Bits((8 * rdiParams.width).W))
    val data_buff_rcv_fill_reg = RegInit(false.B)

    val snd_success_rdi = Wire(Bool())
    
    // Spec Reference: Section 8.4.4 (Mainband Stall State)
    val stall_reg = RegInit(false.B)

    io.mainband_stalldone := stall_reg

    // Spec Reference: Section 8.4.4.1 (Stall Request Handling)
    // protocol -> d2d -> phy
    when(io.mainband_stallreq){
        stall_reg := true.B
    }.elsewhen(io.d2d_state =/= PhyState.active){
        stall_reg := false.B
    }.otherwise{
        stall_reg := stall_reg
    }

    // Spec Reference: Section 9.2.1.1 (Parity Data Multiplexing)
    when(io.parity_insert){// data path, a mux
        io.rdi_lp_data := io.parity_data
    }.otherwise{
        io.rdi_lp_data := data_buff_snt_reg
    }

    // Spec Reference: Section 9.2.1.2 (Parity Data Capture)
    io.snd_data := io.fdi_lp_data
    io.snd_data_vld := io.fdi_pl_trdy & io.fdi_lp_valid & io.fdi_lp_irdy // give the parity the same data when accept from fdi
    snd_success_rdi := io.rdi_lp_irdy & io.rdi_lp_valid & io.rdi_pl_trdy// one data is send to RDI // another data can proceed

    // Spec Reference: Section 6.2.1.6 (Transmit Flow Control)
    when(!io.parity_insert && data_buff_snt_fill_reg && !stall_reg){
        io.rdi_lp_irdy := true.B
        io.rdi_lp_valid := true.B
    }.elsewhen(io.parity_insert && !stall_reg){
        io.rdi_lp_irdy := true.B
        io.rdi_lp_valid := true.B        
    }.otherwise{
        io.rdi_lp_irdy := false.B
        io.rdi_lp_valid := false.B           
    }

    // Spec Reference: Section 9.2.1.3 (Parity Ready Signal)
    when(io.parity_insert && snd_success_rdi){
        io.parity_rdy := true.B
    }.otherwise{
        io.parity_rdy := false.B
    }
    // Spec Reference: Section 6.2.1.7 (Transmit Buffer Management)
    // what condition does data_buff_snt_reg get refill?
    when(!data_buff_snt_fill_reg){// can accept data from fdi
        io.fdi_pl_trdy := true.B
        when(io.fdi_lp_irdy && io.fdi_lp_valid){ // fdi has data 
            data_buff_snt_fill_reg := true.B
            data_buff_snt_reg := io.fdi_lp_data
        }.otherwise{
            data_buff_snt_fill_reg := false.B
            data_buff_snt_reg := io.fdi_lp_data            
        }
    }.elsewhen(!io.parity_insert && snd_success_rdi){// when data can be sent through rdi...
        io.fdi_pl_trdy := true.B
        when(io.fdi_lp_irdy && io.fdi_lp_valid){// fdi has data  
            data_buff_snt_fill_reg := true.B
            data_buff_snt_reg := io.fdi_lp_data
        }.otherwise{
            data_buff_snt_fill_reg := false.B
            data_buff_snt_reg := data_buff_snt_reg
        }
    }.otherwise{
        io.fdi_pl_trdy := false.B
        data_buff_snt_reg := data_buff_snt_reg     
        data_buff_snt_fill_reg := data_buff_snt_fill_reg   
    }

    // Spec Reference: Section 6.2.1.8 (Receive Path - Protocol Stream Configuration)
    // phy -> d2d -> protocol
    val streaming = Wire(new ProtoStream())
    streaming.protoStack := ProtoStack.stack0
    streaming.protoType := ProtoStreamType.Stream
    io.fdi_pl_stream <> streaming

    // Spec Reference: Section 9.2.1.4 (Parity Data Capture - Receive)
    io.rcv_data := io.rdi_pl_data
    io.rcv_data_vld := io.rdi_pl_valid // give the parity the same data when accept from rdi
    io.fdi_pl_data := data_buff_rcv_reg

    // Spec Reference: Section 6.2.1.9 (Receive Buffer Management)
    when(io.rdi_pl_valid){//push the data
        data_buff_rcv_reg := io.rdi_pl_data
    }.otherwise{
        data_buff_rcv_reg := data_buff_rcv_reg
    }

    // Spec Reference: Section 6.2.1.10 (Receive Flow Control)
    when(data_buff_rcv_fill_reg){
        io.fdi_pl_valid := true.B
    }.otherwise{
        io.fdi_pl_valid := false.B
    }

    // Spec Reference: Section 9.2.1.5 (Parity Check Filtering)
    when(io.rdi_pl_valid && !io.parity_check){// buff will be emptied once there is no actual data come in
        data_buff_rcv_fill_reg := true.B
    }.otherwise{
        data_buff_rcv_fill_reg := false.B
    }

}