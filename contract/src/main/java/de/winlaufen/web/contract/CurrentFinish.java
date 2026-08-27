package de.winlaufen.web.contract;

/** Zero-based row index into the referenced class snapshot. This is not a rank. */
public record CurrentFinish(int classIndex, int rowIndex, long snapshotSourceRevision) { }
