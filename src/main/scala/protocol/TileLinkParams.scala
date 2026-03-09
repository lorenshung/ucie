package edu.berkeley.cs.ucie.digital
package protocol

/**
  * Minimal TileLink field sizing used by the UCIe protocol layer pack/unpack bundles.
  *
  * This project currently uses width metadata only (no RocketChip TileLink dependency).
  */
case class TileLinkParams(
    addressWidth: Int = 64,
    opcodeWidth: Int = 3,
    paramWidth: Int = 3,
    sizeWidth: Int = 4,
    sourceIDWidth: Int = 8,
    sinkIDWidth: Int = 2,
    maskWidth: Int = 8,
    reservedH2Width: Int = 33,
)

