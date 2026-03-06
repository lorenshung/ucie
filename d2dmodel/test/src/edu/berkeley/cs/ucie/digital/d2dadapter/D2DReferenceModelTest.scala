package edu.berkeley.cs.ucie.digital
package d2dadapter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import edu.berkeley.cs.uciedigital.d2dadapter.model._

/**
 * Verifies the D2D reference model in isolation (no RTL).
 * Expected values are hand-derived from the intended behavior so the model
 * can serve as a trusted golden reference for RTL verification.
 */
class D2DReferenceModelTest extends AnyFlatSpec with Matchers {

  val config = D2DModelConfig(dataWidthBytes = 8)
  val n = config.dataWidthBytes

  def dataBytes(x: Long): Seq[Byte] =
    (0 until n).reverse.map { i => ((x >> (i * 8)) & 0xff).toByte }

  behavior of "D2DReferenceModel step (golden verification)"

  it should "stay idle when link is not active (reset state)" in {
    val state = D2DModelState(
      linkMgr = LinkMgrState(linkState = 0) // reset
    )
    val fdiLp = FdiLpSignals(irdy = true, valid = true, data = dataBytes(0xAB))
    val rdiPl = RdiPlSignals(trdy = true)

    val (fdiPl, rdiLp, nextState) = D2DReferenceModel.step(config, state, fdiLp, rdiPl)

    fdiPl.trdy shouldBe false
    fdiPl.valid shouldBe false
    rdiLp.irdy shouldBe false
    rdiLp.valid shouldBe false
    nextState.mainband.dataBuffSntFill shouldBe false
  }

  it should "accept one FDI beat when link is active and buffer empty" in {
    val state = D2DModelState(
      linkMgr = LinkMgrState(linkState = 1),
      mainband = MainbandState()
    )
    val fdiLp = FdiLpSignals(irdy = true, valid = true, data = dataBytes(0xDEADBEEFL))
    val rdiPl = RdiPlSignals(trdy = true)

    val (fdiPl, rdiLp, nextState) = D2DReferenceModel.step(config, state, fdiLp, rdiPl)

    fdiPl.trdy shouldBe true
    nextState.mainband.dataBuffSntFill shouldBe true
    nextState.mainband.dataBuffSnt shouldBe BigInt(0xDEADBEEFL) & ((BigInt(1) << 64) - 1)
    rdiLp.irdy shouldBe false
    rdiLp.valid shouldBe false
  }

  it should "drain send buffer one cycle after accept when RDI is ready" in {
    val state = D2DModelState(
      linkMgr = LinkMgrState(linkState = 1),
      mainband = MainbandState(dataBuffSnt = 0xCAFEL, dataBuffSntFill = true)
    )
    val fdiLp = FdiLpSignals()
    val rdiPl = RdiPlSignals(trdy = true)

    val (fdiPl, rdiLp, nextState) = D2DReferenceModel.step(config, state, fdiLp, rdiPl)

    rdiLp.irdy shouldBe true
    rdiLp.valid shouldBe true
    rdiLp.data shouldBe dataBytes(0xCAFEL)
    nextState.mainband.dataBuffSntFill shouldBe false
    fdiPl.trdy shouldBe true
  }

  it should "not assert RDI valid when physical layer not ready (trdy false)" in {
    val state = D2DModelState(
      linkMgr = LinkMgrState(linkState = 1),
      mainband = MainbandState(dataBuffSnt = 0x42L, dataBuffSntFill = true)
    )
    val fdiLp = FdiLpSignals()
    val rdiPl = RdiPlSignals(trdy = false)

    val (_, rdiLp, nextState) = D2DReferenceModel.step(config, state, fdiLp, rdiPl)

    rdiLp.irdy shouldBe true
    rdiLp.valid shouldBe true
    nextState.mainband.dataBuffSntFill shouldBe true
  }

  it should "propagate RDI to FDI with one-cycle latency (receive path)" in {
    val state = D2DModelState(linkMgr = LinkMgrState(linkState = 1))

    val (_, _, state1) = D2DReferenceModel.step(config, state, FdiLpSignals(), RdiPlSignals(valid = true, data = dataBytes(0x12345678L)))
    state1.mainband.dataBuffRcvFill shouldBe true
    state1.mainband.dataBuffRcv shouldBe BigInt(0x12345678L) & ((BigInt(1) << 64) - 1)

    val (fdiPl2, _, _) = D2DReferenceModel.step(config, state1, FdiLpSignals(), RdiPlSignals())
    fdiPl2.valid shouldBe true
    fdiPl2.data shouldBe dataBytes(0x12345678L)
  }

  it should "preserve FDI-to-RDI data over two cycles (hand-derived trace)" in {
    val state0 = D2DModelState(linkMgr = LinkMgrState(linkState = 1))
    val pay = 0xABCDEF01L

    val (fdiPl1, rdiLp1, state1) = D2DReferenceModel.step(
      config, state0,
      FdiLpSignals(irdy = true, valid = true, data = dataBytes(pay)),
      RdiPlSignals(trdy = true)
    )
    fdiPl1.trdy shouldBe true
    state1.mainband.dataBuffSntFill shouldBe true
    rdiLp1.irdy shouldBe false
    rdiLp1.valid shouldBe false

    val (_, rdiLp2, state2) = D2DReferenceModel.step(config, state1, FdiLpSignals(), RdiPlSignals(trdy = true))
    rdiLp2.data shouldBe dataBytes(pay)
    rdiLp2.irdy shouldBe true
    rdiLp2.valid shouldBe true
    state2.mainband.dataBuffSntFill shouldBe false

    val (_, rdiLp3, _) = D2DReferenceModel.step(config, state2, FdiLpSignals(), RdiPlSignals(trdy = true))
    rdiLp3.irdy shouldBe false
    rdiLp3.valid shouldBe false
  }

  it should "forward link error from FDI to RDI (one-cycle latency)" in {
    val state = D2DModelState(linkMgr = LinkMgrState(linkState = 1))
    val (_, rdiLp1, state1) = D2DReferenceModel.step(
      config, state,
      FdiLpSignals(linkError = true),
      RdiPlSignals()
    )
    state1.linkMgr.rdiLpLinkerror shouldBe true
    rdiLp1.linkError shouldBe false
    val (_, rdiLp2, _) = D2DReferenceModel.step(config, state1, FdiLpSignals(), RdiPlSignals())
    rdiLp2.linkError shouldBe true
  }
}
