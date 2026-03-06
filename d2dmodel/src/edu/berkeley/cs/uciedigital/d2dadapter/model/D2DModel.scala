package edu.berkeley.cs.uciedigital.d2dadapter.model

// Configuration class to define the widths based on the specific UCIe module package
case class UCIeConfig(nBytes: Int, ncWidth: Int, vsWidth: Int, nDllp: Int)

// ==========================================
// Raw Die-to-Die Interface (RDI)
// ==========================================

// Adapter to Physical Layer (lp_*)
case class RdiLpSignals(
  irdy: Boolean = false,
  valid: Boolean = false,
  data: Seq[Byte] = Seq.empty,        // Length should match config.nBytes
  retimerCrd: Boolean = false,
  stateReq: Int = 0,                  // 4-bit representation
  linkError: Boolean = false,
  stallAck: Boolean = false,
  clkAck: Boolean = false,
  wakeReq: Boolean = false,
  cfg: BigInt = 0,                    // Variable width, BigInt handles > 64 bits easily
  cfgVld: Boolean = false,
  cfgCrd: Boolean = false,
  vendorDefined: BigInt = 0
)

// Physical Layer to Adapter (pl_*)
case class RdiPlSignals(
  trdy: Boolean = false,
  valid: Boolean = false,
  data: Seq[Byte] = Seq.empty,        // Length should match config.nBytes
  retimerCrd: Boolean = false,
  stateReq: Int = 0,                  // 4-bit representation 
  // 0 = reset, 1 = active , 3 = active.pmnak, 4 = l1, 8 = l2, 9 = linkReset, 10 = linkError, 11 = retrain, 12 = disabled
  inbandPres: Boolean = false,
  error: Boolean = false,
  cerror: Boolean = false,
  nferror: Boolean = false,
  trainError: Boolean = false,
  phyInRecenter: Boolean = false,
  stallReq: Boolean = false,
  speedMode: Int = 0,                 // 3-bit representation
  maxSpeedMode: Boolean = false,
  lnkCfg: Int = 0,                    // 3-bit representation
  clkReq: Boolean = false,
  wakeAck: Boolean = false,
  cfg: BigInt = 0,
  cfgVld: Boolean = false,
  cfgCrd: Boolean = false,
  vendorDefined: BigInt = 0
)

// ==========================================
// Flit-Aware Die-to-Die Interface (FDI)
// ==========================================

// Protocol Layer to Adapter (lp_*)
case class FdiLpSignals(
  irdy: Boolean = false,
  valid: Boolean = false,
  data: Seq[Byte] = Seq.empty,
  retimerCrd: Boolean = false,
  corruptCrc: Boolean = false,
  dllp: Seq[Byte] = Seq.empty,        // Length should match config.nDllp
  dllpValid: Boolean = false,
  dllpOfc: Boolean = false,
  stream: Int = 0,                    // 8-bit stream ID
  // State management signals (similar to RDI depending on protocol)
  stateReq: Int = 0,                 // 4-bit representation
  linkError: Boolean = false
)

// Adapter to Protocol Layer (pl_*)
case class FdiPlSignals(
  trdy: Boolean = false,
  valid: Boolean = false,
  data: Seq[Byte] = Seq.empty,
  retimerCrd: Boolean = false,
  // State management signals (similar to RDI depending on protocol)
  stateSts: Int = 0,
  inbandPres: Boolean = false,
  error: Boolean = false
)

// ==========================================
// Interface Containers
// ==========================================

// Container for the complete RDI interface state at a given clock cycle
case class RdiState(
  lp: RdiLpSignals,
  pl: RdiPlSignals
)

// Container for the complete FDI interface state at a given clock cycle
case class FdiState(
  lp: FdiLpSignals,
  pl: FdiPlSignals
)
