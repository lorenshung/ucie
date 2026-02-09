package edu.berkeley.cs.ucie.digital.d2dadapter

import chisel3._
//import chisel3.util._
//import interfaces._
//import sideband._

// Spec Reference: Section 8.2.1 (Link Initialization State Machine)
// LinkInitModule constants

object LinkInitState extends ChiselEnum {
    val INIT_START = Value(0x0.U(3.W))      // Spec: Initial state waiting for RDI inband presence
    val RDI_BRINGUP = Value(0x1.U(3.W))    // Spec: RDI bringup phase
    val PARAM_EXCH = Value(0x2.U(3.W))     // Spec: Parameter exchange via sideband
    val FDI_BRINGUP = Value(0x3.U(3.W))   // Spec: FDI bringup phase
    val INIT_DONE = Value(0x4.U(3.W))     // Spec: Initialization complete, ready for Active state
}

// Spec Reference: Section 7.3 (Sideband Message Encodings)
// Sideband constants

object D2DAdapterSignalSize{
    val SIDEBAND_MESSAGE_OP_WIDTH = 6.W
}

object SideBandMessage{
    // Spec Reference: Section 7.3.1 (Sideband Message Format)
    // start with 01: RES
    // start with 00: REQ
    // start with 1: others
    val NOP: UInt = "b000000".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 8.2.2.1 (Active State Request/Response)
    val REQ_ACTIVE: UInt = "b000001".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    val RSP_ACTIVE: UInt = "b010001".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 8.2.3 (L1 Power State Request/Response)
    val REQ_L1: UInt = "b000100".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    val RSP_L1: UInt = "b010100".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 8.2.4 (L2 Power State Request/Response)
    val REQ_L2: UInt = "b001000".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    val RSP_L2: UInt = "b011000".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 8.3.2 (Link Reset Request/Response)
    val REQ_LINKRESET: UInt = "b001001".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    val RSP_LINKRESET: UInt = "b011001".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 8.3.1 (Disabled State Request/Response)
    val REQ_DISABLED: UInt = "b001100".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    val RSP_DISABLED: UInt = "b011100".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 8.2.2.2 (Power Management Negative Acknowledge)
    val RSP_PMNAK: UInt = "b010011".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 9.3.2 (Parity Feature Negotiation Messages)
    val PARITY_FEATURE_REQ: UInt = "b100001".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    val PARITY_FEATURE_ACK: UInt = "b110001".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    val PARITY_FEATURE_NAK: UInt = "b110010".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 8.2.1.2 (Advertised Capabilities Message)
    val ADV_CAP: UInt = "b100100".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
    
    // Spec Reference: Section 7.4 (Register Access Messages)
    val REGISTER_ACCESS: UInt = "b101000".U(D2DAdapterSignalSize.SIDEBAND_MESSAGE_OP_WIDTH)
}

// Spec Reference: Section 8.4 (Stall Handshake Protocol State Machine)
// Stall Handler constants

object StallHandlerWidth{
    val STATE_WIDTH = 2.W
}

object StallHandshakeState extends ChiselEnum{
    val IDLE = Value(0x0.U(StallHandlerWidth.STATE_WIDTH))      // Spec: No stall request pending
    val REQSNT = Value(0x1.U(StallHandlerWidth.STATE_WIDTH))    // Spec: Stall request sent, waiting for acknowledge
    val REQFALL = Value(0x2.U(StallHandlerWidth.STATE_WIDTH))    // Spec: Stall acknowledge received, waiting for deassertion
    val COMPLETE = Value(0x3.U(StallHandlerWidth.STATE_WIDTH))   // Spec: Stall handshake complete
}

// Spec Reference: Section 9.2 (Parity Generation and Checking)
// Parity module constants

object ParityGeneratorWidth{
    val PARITY_N_WIDTH = 3.W
}

// Spec Reference: Section 9.2.1 (Parity Block Sizes)
object ParityAmount{
    val BASESIZE: Int = 64
    val PARITY_DATA_NBYTE_1: Int = 64      // Spec: 1 parity block = 64 bytes
    val DATA_NBYTE_1: Int = 256 * 256 * 1  // Spec: Data block size for 1 parity block
    val PARITY_DATA_NBYTE_2: Int = 64 * 2  // Spec: 2 parity blocks = 128 bytes
    val DATA_NBYTE_2: Int = 256 * 256 * 2  // Spec: Data block size for 2 parity blocks
    val PARITY_DATA_NBYTE_4: Int = 64 * 4  // Spec: 4 parity blocks = 256 bytes
    val DATA_NBYTE_4: Int = 256 * 256 * 4  // Spec: Data block size for 4 parity blocks
    val CORRECT_REG_WIDTH = 256.W // 4 * 64 for maximum four 64Bytes parity
}

// Spec Reference: Section 9.2.2 (Parity Block Count Configuration)
object ParityN{
    val ONE: UInt = "b000".U   // Spec: 1 parity block per data block
    val TWO: UInt = "b001".U   // Spec: 2 parity blocks per data block
    val FOUR: UInt = "b010".U  // Spec: 4 parity blocks per data block
}