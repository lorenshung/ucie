package freechips.rocketchip.util

import chisel3._
import chisel3.util._

case class AsyncQueueParams(depth: Int, sync: Int = 2)

/**
  * Minimal async FIFO with RocketChip-compatible IO shape.
  *
  * This is intended to satisfy `freechips.rocketchip.util.AsyncQueue` usages in this repo.
  */
class AsyncQueue[T <: Data](gen: T, params: AsyncQueueParams) extends RawModule {
  require(params.depth > 0, "AsyncQueue depth must be > 0")
  require(params.sync >= 2, "AsyncQueue synchronizer must be >= 2 stages")

  val io = IO(new Bundle {
    val enq_clock = Input(Clock())
    val enq_reset = Input(Reset())
    val enq = Flipped(Decoupled(gen))

    val deq_clock = Input(Clock())
    val deq_reset = Input(Reset())
    val deq = Decoupled(gen)
  })

  private val depth = params.depth
  private val addrWidth = log2Ceil(depth)
  private val ptrWidth = addrWidth + 1

  private def bin2gray(x: UInt): UInt = (x >> 1) ^ x

  private def syncGray(in: UInt, clock: Clock, reset: Reset): UInt =
    withClockAndReset(clock, reset) {
      val regs = RegInit(VecInit(Seq.fill(params.sync)(0.U(in.getWidth.W))))
      regs(0) := in
      (1 until params.sync).foreach { i => regs(i) := regs(i - 1) }
      regs(params.sync - 1)
    }

  // Dual-clock memory: write in enq clock domain, read address in deq clock domain.
  private val mem = Mem(depth, gen)

  // Enqueue domain pointers
  private val wbin = withClockAndReset(io.enq_clock, io.enq_reset) { RegInit(0.U(ptrWidth.W)) }
  private val wgray = withClockAndReset(io.enq_clock, io.enq_reset) { RegInit(0.U(ptrWidth.W)) }

  // Dequeue domain pointers
  private val rbin = withClockAndReset(io.deq_clock, io.deq_reset) { RegInit(0.U(ptrWidth.W)) }
  private val rgray = withClockAndReset(io.deq_clock, io.deq_reset) { RegInit(0.U(ptrWidth.W)) }

  // Cross-domain pointer synchronization
  private val rgraySync = syncGray(rgray, io.enq_clock, io.enq_reset)
  private val wgraySync = syncGray(wgray, io.deq_clock, io.deq_reset)

  // Full computation (enqueue side)
  private val wbinNext = wbin + (io.enq.valid && io.enq.ready).asUInt
  private val wgrayNext = bin2gray(wbinNext)
  private val rgraySyncInv =
    if (ptrWidth == 1) {
      (~rgraySync)(0, 0)
    } else if (ptrWidth == 2) {
      (~rgraySync)(1, 0)
    } else {
      Cat(~rgraySync(ptrWidth - 1, ptrWidth - 2), rgraySync(ptrWidth - 3, 0))
    }
  private val full = wgrayNext === rgraySyncInv

  io.enq.ready := !full

  // Empty computation (dequeue side)
  private val empty = wgraySync === rgray
  io.deq.valid := !empty

  private val raddr = if (addrWidth == 0) 0.U else rbin(addrWidth - 1, 0)
  io.deq.bits := mem(raddr)

  // Enqueue write + pointer update
  withClock(io.enq_clock) {
    when(io.enq.fire) {
      val waddr = if (addrWidth == 0) 0.U else wbin(addrWidth - 1, 0)
      mem.write(waddr, io.enq.bits)
    }
  }
  withClockAndReset(io.enq_clock, io.enq_reset) {
    when(io.enq.fire) {
      wbin := wbinNext
      wgray := wgrayNext
    }
  }

  // Dequeue pointer update (data is read combinationally via `mem(raddr)`)
  private val rbinNext = rbin + (io.deq.valid && io.deq.ready).asUInt
  private val rgrayNext = bin2gray(rbinNext)
  withClockAndReset(io.deq_clock, io.deq_reset) {
    when(io.deq.valid && io.deq.ready) {
      rbin := rbinNext
      rgray := rgrayNext
    }
  }
}

