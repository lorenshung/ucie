package edu.berkeley.cs.uciedigital.d2dadapter.model

/** Fixed data path width in bytes (must match RTL fdiParams.width / rdiParams.width). */
case class D2DModelConfig(dataWidthBytes: Int = 8)

// ==========================================
// Internal state mirrors of RTL registers
// ==========================================

/** Mainband: data_buff_snt_reg, data_buff_snt_fill_reg, data_buff_rcv_reg, data_buff_rcv_fill_reg, stall_reg. */
case class MainbandState(
  dataBuffSnt: BigInt = 0,
  dataBuffSntFill: Boolean = false,
  dataBuffRcv: BigInt = 0,
  dataBuffRcvFill: Boolean = false,
  stall: Boolean = false
)

/** Link manager: link_state_reg, rdi_lp_linkerror_reg, rdi_lp_state_req_reg, fdi_pl_*_reg, linkmgmt_stallreq_reg, fdi_lp_state_req_prev_reg. */
case class LinkMgrState(
  linkState: Int = 0,           // PhyState: 0=reset, 1=active, 3=activePmNak, 4=l1, 8=l2, 9=linkReset, 10=linkError, 11=retrain, 12=disabled
  rdiLpLinkerror: Boolean = false,
  rdiLpStateReq: Int = 0,       // PhyStateReq: 0=nop, 1=active, 4=l1, 8=l2, 9=linkReset, 11=retrain, 12=disabled
  fdiPlRxactiveReq: Boolean = false,
  fdiPlInbandPres: Boolean = false,
  linkmgmtStallreq: Boolean = false,
  fdiLpStateReqPrev: Int = 0
)

/** Stall handshake: 0=IDLE, 1=REQSNT, 2=REQFALL, 3=COMPLETE. */
case class StallHandlerState(handshakeState: Int = 0)

/** Placeholder for parity / sideband; expand when modeling those paths. */
case class AuxState(
  parityInsert: Boolean = false,
  parityCheck: Boolean = false,
  parityData: BigInt = 0
)

/** Top-level cycle-accurate model state: one instance per D2D adapter. */
case class D2DModelState(
  mainband: MainbandState = MainbandState(),
  linkMgr: LinkMgrState = LinkMgrState(),
  fdiStall: StallHandlerState = StallHandlerState(),
  rdiStall: StallHandlerState = StallHandlerState(),
  aux: AuxState = AuxState()
)

// ==========================================
// Step function: one cycle
// ==========================================

object D2DReferenceModel {
  /** One cycle: (current state, FDI lp inputs, RDI pl inputs) => (FDI pl outputs, RDI lp outputs, next state). */
  def step(
    config: D2DModelConfig,
    state: D2DModelState,
    fdiLp: FdiLpSignals,
    rdiPl: RdiPlSignals
  ): (FdiPlSignals, RdiLpSignals, D2DModelState) = {
  val n = config.dataWidthBytes
  def padData(bytes: Seq[Byte], len: Int): Seq[Byte] =
    if (bytes.size >= len) bytes.take(len) else bytes ++ Seq.fill(len - bytes.size)(0.toByte)
  def bytesToBigInt(bytes: Seq[Byte], len: Int): BigInt =
    padData(bytes, len).foldLeft(BigInt(0)) { (acc, b) => (acc << 8) | (b & 0xff) }
  def bigIntToBytes(v: BigInt, len: Int): Seq[Byte] = {
    val mask = (BigInt(1) << (len * 8)) - 1
    val x = v & mask
    (0 until len).reverse.map { i => ((x >> (i * 8)) & 0xff).toByte }
  }

  val mb = state.mainband
  val lm = state.linkMgr

  // ----- Mainband (skeleton): mirror D2DMainbandModule combinational + next state -----
  val activeState = 1
  val sndSuccessRdi = mb.dataBuffSntFill && !state.aux.parityInsert && !mb.stall && rdiPl.trdy
  val rdiLpIrdy = (mb.dataBuffSntFill && !state.aux.parityInsert && !mb.stall) || (state.aux.parityInsert && !mb.stall)
  val rdiLpValid = rdiLpIrdy
  val canAcceptFdi = !mb.dataBuffSntFill || (sndSuccessRdi && !state.aux.parityInsert)
  val fdiPlTrdy = canAcceptFdi && lm.linkState == activeState
  val acceptFdi = fdiPlTrdy && fdiLp.irdy && fdiLp.valid
  val nextSntFill = if (acceptFdi) true else if (sndSuccessRdi && !acceptFdi) false else mb.dataBuffSntFill
  val nextSntData = if (acceptFdi) bytesToBigInt(fdiLp.data, n) else if (sndSuccessRdi && !acceptFdi) mb.dataBuffSnt else mb.dataBuffSnt
  val nextRcvData = if (rdiPl.valid) bytesToBigInt(rdiPl.data, n) else mb.dataBuffRcv
  val nextRcvFill = rdiPl.valid && !state.aux.parityCheck
  val nextStall = if (state.rdiStall.handshakeState != 0) true else if (lm.linkState != activeState) false else mb.stall

  val nextMb = MainbandState(
    dataBuffSnt = nextSntData,
    dataBuffSntFill = nextSntFill,
    dataBuffRcv = nextRcvData,
    dataBuffRcvFill = nextRcvFill,
    stall = nextStall
  )

  val rdiLpDataBytes = if (state.aux.parityInsert) bigIntToBytes(state.aux.parityData, n) else bigIntToBytes(mb.dataBuffSnt, n)
  val fdiPlDataBytes = bigIntToBytes(mb.dataBuffRcv, n)
  val fdiPlValid = mb.dataBuffRcvFill

  // ----- Link manager (skeleton): forward state, propagate link error -----
  val nextLm = LinkMgrState(
    linkState = lm.linkState,
    rdiLpLinkerror = fdiLp.linkError,
    rdiLpStateReq = lm.rdiLpStateReq,
    fdiPlRxactiveReq = lm.fdiPlRxactiveReq,
    fdiPlInbandPres = lm.fdiPlInbandPres,
    linkmgmtStallreq = lm.linkmgmtStallreq,
    fdiLpStateReqPrev = fdiLp.stateReq
  )

  // ----- Stall handlers: placeholder; no state change -----
  val nextFdiStall = state.fdiStall
  val nextRdiStall = state.rdiStall
  val nextAux = state.aux

  val fdiPl = FdiPlSignals(
    trdy = fdiPlTrdy,
    valid = fdiPlValid,
    data = fdiPlDataBytes,
    stateSts = lm.linkState,
    inbandPres = lm.fdiPlInbandPres,
    error = false
  )
  val rdiLp = RdiLpSignals(
    irdy = rdiLpIrdy,
    valid = rdiLpValid,
    data = rdiLpDataBytes,
    stateReq = lm.rdiLpStateReq,
    linkError = lm.rdiLpLinkerror,
    stallAck = false,
    clkAck = true,
    wakeReq = true
  )
  val nextState = D2DModelState(
    mainband = nextMb,
    linkMgr = nextLm,
    fdiStall = nextFdiStall,
    rdiStall = nextRdiStall,
    aux = nextAux
  )
  (fdiPl, rdiLp, nextState)
  }
}
