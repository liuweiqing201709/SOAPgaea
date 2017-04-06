package org.bgi.flexlab.gaea.tools.realigner.alternateconsensus;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.variant.variantcontext.VariantContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.bgi.flexlab.gaea.data.structure.bam.GaeaAlignedSamRecord;
import org.bgi.flexlab.gaea.data.structure.bam.GaeaCigar;
import org.bgi.flexlab.gaea.data.structure.bam.GaeaSamRecord;
import org.bgi.flexlab.gaea.tools.mapreduce.realigner.RealignerOptions.AlternateConsensusModel;
import org.bgi.flexlab.gaea.util.AlignmentUtil;
import org.bgi.flexlab.gaea.util.BaseUtils;
import org.bgi.flexlab.gaea.util.Pair;

public class AlternateConsensusEngine {
	private AlternateConsensusBin consensusBin = null;
	private AlternateConsensusModel model = null;
	private double mismatchThreshold = 0.15;
	private double mismatchColumnCleanedFraction = 0.75;

	public AlternateConsensusEngine() {
		consensusBin = new AlternateConsensusBin();
		model = AlternateConsensusModel.READS;
	}

	public AlternateConsensusEngine(AlternateConsensusModel model,
			double threshold, double cleanThreshold) {
		consensusBin = new AlternateConsensusBin();
		this.model = model;
		this.mismatchThreshold = threshold;
		this.mismatchColumnCleanedFraction = cleanThreshold;
	}

	public int mismatchQualitySumIgnoreCigar(GaeaAlignedSamRecord read,
			byte[] ref, int posOnRef, int threshold) {
		int MAX_QUALITY = 99;
		int mismatchQualitySum = 0;

		byte[] readSeq = read.getReadBases();
		byte[] qualities = read.getReadQualities();

		for (int readIndex = 0; readIndex < readSeq.length; readIndex++) {
			if (posOnRef >= ref.length) {
				mismatchQualitySum += (readSeq.length - readIndex)
						* MAX_QUALITY;
				break;
			}
			byte refBase = ref[posOnRef++];
			byte readBase = readSeq[readIndex];

			if (BaseUtils.isRegularAndNotEqualBase(readBase, refBase)) {
				mismatchQualitySum += qualities[readIndex];
				if (mismatchQualitySum > threshold)
					return -1;
			}
		}

		if (mismatchQualitySum > threshold)
			mismatchQualitySum = -1;

		return mismatchQualitySum;
	}
	
	public void consensusByKnowIndels(
			final TreeSet<VariantContext> knowIndels,
			final int leftmostIndex, final byte[] reference) {
		for (VariantContext variant : knowIndels) {
			if (variant == null || variant.isComplexIndel()
					|| !variant.isIndel())
				continue;
			byte[] indelStr;
			if (variant.isSimpleInsertion()) {
				byte[] indel = variant.getAlternateAllele(0).getBases();
				indelStr = new byte[indel.length - 1];
				System.arraycopy(indel, 1, indelStr, 0, indel.length - 1);
			} else {
				indelStr = new byte[variant.getReference().length() - 1];
				Arrays.fill(indelStr, (byte) '-');
			}
			consensusBin.addAlternateConsensus(variant.getStart()
					- leftmostIndex + 1, reference, variant, indelStr);
		}
	}

	public long consensusByReads(final List<GaeaSamRecord> reads,
			final ArrayList<GaeaSamRecord> perfectlyReads,
			final ArrayList<GaeaAlignedSamRecord> altReads,
			final LinkedList<GaeaAlignedSamRecord> SWReads,
			final int leftmostIndex, final byte[] reference) {
		long totalRawMismatchSum = 0L;

		for (final GaeaSamRecord read : reads) {
			if (read.getCigar() == null)
				continue;
			if (read.getCigar().numCigarElements() == 0) {
				perfectlyReads.add(read);
				continue;
			}

			final GaeaAlignedSamRecord alignedRead = new GaeaAlignedSamRecord(
					read);

			int numBlocks = GaeaCigar.numberOfMatchCigarOperator(read
					.getCigar());

			if (numBlocks == 2) {
				Cigar newCigar = AlignmentUtil.leftAlignIndel(read.getCigar(),
						reference, read.getReadBases(),
						read.getAlignmentStart() - leftmostIndex, 0);
				alignedRead.setCigar(newCigar, false);
			}

			final int startOnRef = read.getAlignmentStart() - leftmostIndex;

			int rawMismatchScore = mismatchQualitySumIgnoreCigar(alignedRead,
					reference, startOnRef, Integer.MAX_VALUE);

			if (rawMismatchScore != 0) {
				altReads.add(alignedRead);

				if (!read.getDuplicateReadFlag())
					totalRawMismatchSum += rawMismatchScore;
				alignedRead.setMismatchScore(rawMismatchScore);
				alignedRead.setAlignerMismatchScore(AlignmentUtil
						.mismatchQualityCount(alignedRead, reference,
								startOnRef));

				if (model != AlternateConsensusModel.DBSNP
						&& numBlocks == 2) {
					consensusBin.addAlternateConsensus(reference, startOnRef,
							alignedRead.getReadBases(), alignedRead.getCigar());
				} else {
					SWReads.add(alignedRead);
				}
			} else {
				perfectlyReads.add(read);
			}
		}

		return totalRawMismatchSum;
	}
	
	// sw function is interface
	public void consensusBySmithWaterman(){
		if(model != AlternateConsensusModel.SW)
			return;
	}

	public AlternateConsensus findBestAlternateConsensus(
			ArrayList<GaeaAlignedSamRecord> reads, int leftMostIndex) {
		AlternateConsensus bestConsensus = null;
		Iterator<AlternateConsensus> iter = consensusBin.get().iterator();

		while (iter.hasNext()) {
			AlternateConsensus currentConsensus = iter.next();

			for (int i = 0; i < reads.size(); i++) {
				GaeaAlignedSamRecord read = reads.get(i);
				Pair<Integer, Integer> best = findBestOffset(
						currentConsensus.getSequence(), read, leftMostIndex);

				int readScore = best.second;
				if (readScore >= read.getAlignerMismatchScore()
						|| readScore > read.getMismatchScore())
					readScore = read.getAlignerMismatchScore();
				else
					currentConsensus.add(new Pair<Integer, Integer>(i, best.first));

				if (!read.getRead().isDuplicateRead())
					currentConsensus.addMismatch(readScore);

				if (bestConsensus != null
						&& bestConsensus.getMismatch() < currentConsensus
								.getMismatch())
					break;
			}

			if (bestConsensus == null
					|| (bestConsensus != null && bestConsensus.getMismatch() > currentConsensus
							.getMismatch())) {
				if (bestConsensus != null) {
					bestConsensus.clear();
				}
				bestConsensus = currentConsensus;
			} else {
				currentConsensus.clear();
			}
		}

		return bestConsensus;
	}

	private boolean badCigar(Cigar cigar) {
		CigarOperator op1 = cigar.getCigarElement(0).getOperator();
		CigarOperator op2 = cigar.getCigarElement(1).getOperator();

		if (op1 == CigarOperator.I) {
			if (op2 != CigarOperator.M)
				return true;
		} else if (op1 != CigarOperator.M) {
			return true;
		} else {
			if (op2 != CigarOperator.D && op2 != CigarOperator.I)
				return true;
		}
		return false;
	}

	public boolean needRealignment(ArrayList<GaeaAlignedSamRecord> reads,
			byte[] ref, int leftMostIndex) {
		if (model != AlternateConsensusModel.DBSNP
				&& !lookForEntropy(reads, ref, leftMostIndex))
			return true;
		return false;
	}

	public boolean updateRead(final Cigar consensusCigar, final int posOnRef,
			final int posOnConsensus, final GaeaAlignedSamRecord alignedRead,
			final int leftMostIndex) {
		Cigar newCigar = new Cigar();

		if (consensusCigar.getCigarElements().size() == 1) {
			alignedRead.setNewStart(leftMostIndex + posOnConsensus);
			newCigar.add(new CigarElement(alignedRead.getReadLength(),
					CigarOperator.M));
			alignedRead.setCigar(newCigar);
			return true;
		}

		if (badCigar(consensusCigar))
			return false;

		CigarElement altCigarElement1 = consensusCigar.getCigarElement(0);
		CigarElement altCigarElement2 = consensusCigar.getCigarElement(1);

		int matchBlockLength = 0;

		CigarElement indelCE = null;
		if (altCigarElement1.getOperator() == CigarOperator.I) {
			indelCE = altCigarElement1;
		} else {
			if (altCigarElement2.getOperator() == CigarOperator.I
					|| altCigarElement2.getOperator() == CigarOperator.D) {
				indelCE = altCigarElement2;
			}
			matchBlockLength = altCigarElement1.getLength();
		}

		int endOfMatchBlock = posOnRef + matchBlockLength;
		boolean sawAlignmentStart = false;

		if (posOnConsensus < endOfMatchBlock) {
			alignedRead.setNewStart(leftMostIndex + posOnConsensus);
			sawAlignmentStart = true;

			if (posOnConsensus + alignedRead.getReadLength() <= endOfMatchBlock) {
				alignedRead.setCigar(null);
				return true;
			}
			newCigar.add(new CigarElement(endOfMatchBlock - posOnConsensus,
					CigarOperator.M));
		}

		if (indelCE.getOperator() == CigarOperator.I) {
			if (posOnConsensus + alignedRead.getReadLength() < endOfMatchBlock
					+ indelCE.getLength()) {
				int partialInsertionLength = posOnConsensus
						+ alignedRead.getReadLength() - endOfMatchBlock;
				if (!sawAlignmentStart)
					partialInsertionLength = alignedRead.getReadLength();
				newCigar.add(new CigarElement(partialInsertionLength,
						CigarOperator.I));
				alignedRead.setCigar(newCigar);
				return true;
			}

			if (!sawAlignmentStart
					&& posOnConsensus < endOfMatchBlock + indelCE.getLength()) {
				alignedRead.setNewStart(leftMostIndex + endOfMatchBlock);
				newCigar.add(new CigarElement(indelCE.getLength()
						- (posOnConsensus - endOfMatchBlock), CigarOperator.I));
				sawAlignmentStart = true;
			} else if (sawAlignmentStart) {
				newCigar.add(indelCE);
			}
		} else if (indelCE.getOperator() == CigarOperator.D) {
			if (sawAlignmentStart)
				newCigar.add(indelCE);
		}

		if (!sawAlignmentStart) {
			alignedRead.setCigar(null);
			return true;
		}

		int readRemaining = alignedRead.getReadLength();
		for (CigarElement ce : newCigar.getCigarElements()) {
			if (ce.getOperator() != CigarOperator.D)
				readRemaining -= ce.getLength();
		}
		if (readRemaining > 0)
			newCigar.add(new CigarElement(readRemaining, CigarOperator.M));
		alignedRead.setCigar(newCigar);

		return true;
	}

	private boolean lookForEntropy(ArrayList<GaeaAlignedSamRecord> reads,
			byte[] ref, int leftMostIndex) {
		int refLength = ref.length;
		long[] rawMismatchQuality = new long[refLength];
		long[] cleanMismatchQuality = new long[refLength];
		long[] totalRawQuality = new long[refLength];
		long[] totalCleanQuality = new long[refLength];

		for (GaeaAlignedSamRecord read : reads) {
			if (read.getRead().getAlignmentBlocks().size() > 1)
				continue;

			int refIndex = read.getRead().getAlignmentStart() - leftMostIndex;
			byte[] seq = read.getReadBases();
			byte[] quality = read.getReadQualities();

			int i;
			for (i = 0; i < seq.length; i++) {
				if (refIndex < 0 || refIndex > refLength)
					break;
				totalRawQuality[refIndex] += quality[i];
				if (ref[refIndex] != seq[i])
					rawMismatchQuality[refIndex] += quality[i];
				refIndex++;
			}

			refIndex = read.getAlignmentStart() - leftMostIndex;
			int readIndex = 0;
			for (CigarElement ce : read.getCigar().getCigarElements()) {
				int length = ce.getLength();
				switch (ce.getOperator()) {
				case M:
				case X:
				case EQ:
					for (i = 0; i < length; i++, refIndex++, readIndex++) {
						if (refIndex >= refLength)
							break;
						totalCleanQuality[refIndex] += quality[readIndex];
						if (ref[refIndex] != seq[readIndex])
							cleanMismatchQuality[refIndex] += quality[readIndex];
					}
					break;
				case D:
					refIndex += length;
					break;
				case I:
					readIndex += length;
				default:
					break;
				}
			}
		}

		int rawColumns = 0, cleanedColumns = 0;
		for (int i = 0; i < refLength; i++) {
			if (rawMismatchQuality[i] == cleanMismatchQuality[i])
				continue;
			if (rawMismatchQuality[i] > cleanMismatchQuality[i]
					* mismatchThreshold) {
				rawColumns++;
				if (totalCleanQuality[i] > 0
						&& ((double) cleanMismatchQuality[i] / (double) totalCleanQuality[i]) > ((double) rawMismatchQuality[i] / (double) totalRawQuality[i])
								* (1.0 - mismatchColumnCleanedFraction)) {
					cleanedColumns++;
				}
			} else if (cleanMismatchQuality[i] > totalCleanQuality[i]
					* mismatchThreshold) {
				cleanedColumns++;
			}
		}

		return (rawColumns == 0 || cleanedColumns < rawColumns);
	}

	private Pair<Integer, Integer> findBestOffset(final byte[] ref,
			final GaeaAlignedSamRecord read, final int leftmostIndex) {
		final int originalAlignment = read.getRead().getAlignmentStart()
				- leftmostIndex;
		int bestScore = Integer.MAX_VALUE;
		int bestIndex = originalAlignment;
		final int end = ref.length - read.getRead().getReadLength();
		final int maxLength = end - originalAlignment;

		for (int i = 0; i <= maxLength; i++) {
			int left = originalAlignment - i;
			if (left >= 0) {
				int score = mismatchQualitySumIgnoreCigar(read, ref, left,
						bestScore);
				if (score < bestScore) {
					bestScore = score;
					bestIndex = left;
				}

				if (bestScore == 0)
					return new Pair<Integer, Integer>(bestIndex, 0);
			}
			int right = originalAlignment + i;
			if (left == right)
				continue;

			if (right <= end) {
				int score = mismatchQualitySumIgnoreCigar(read, ref, right,
						bestScore);
				if (score < bestScore) {
					bestScore = score;
					bestIndex = right;
				}

				if (bestScore == 0)
					return new Pair<Integer, Integer>(bestIndex, 0);
			}
		}

		return new Pair<Integer, Integer>(bestIndex, bestScore);
	}
}
