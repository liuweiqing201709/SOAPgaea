package org.bgi.flexlab.gaea.util;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;

import java.util.Arrays;

import org.bgi.flexlab.gaea.data.structure.bam.GaeaAlignedSamRecord;
import org.bgi.flexlab.gaea.data.structure.bam.GaeaCigar;

public class AlignmentUtil {

	public static int mismatchQualityCount(GaeaAlignedSamRecord read,
			byte[] ref, int posOnRef) {
		return mismatchQualityCount(read, ref, posOnRef, 0, read.getRead()
				.getReadLength());
	}

	public static int mismatchQualityCount(GaeaAlignedSamRecord read,
			byte[] ref, int posOnRef, int posOnRead, int baseLength) {
		int mismatchQualitySum = 0;

		int readIndex = 0;
		int endIndex = posOnRead + baseLength - 1;

		byte[] readSeq = read.getReadBases();
		byte[] qualities = read.getReadQualities();

		for (CigarElement element : read.getCigar().getCigarElements()) {
			int length = element.getLength();

			switch (element.getOperator()) {
			case M:
				for (int i = 0; i < length; i++, readIndex++, posOnRef++) {
					if (posOnRef > ref.length || readIndex > endIndex) {
						return mismatchQualitySum;
					}
					if (readIndex < posOnRead)
						continue;
					byte readBase = readSeq[readIndex];
					byte refBase = ref[posOnRef];

					if (readBase != refBase)
						mismatchQualitySum += qualities[readIndex];
				}
				break;
			case X:
				for (int i = 0; i < length; i++, readIndex++, posOnRef++) {
					if (posOnRef > ref.length || readIndex > endIndex) {
						return mismatchQualitySum;
					}
					if (readIndex < posOnRead)
						continue;

					mismatchQualitySum += qualities[readIndex];
				}
				break;
			case EQ:
				readIndex += length;
				posOnRef += length;
				break;
			default:
				break;
			}
		}

		return mismatchQualitySum;
	}

	public static byte[] createStringByIndel(Cigar cigar, int indexOfIndel,
			byte[] ref, byte[] read, int refIndex, int readIndex) {
		CigarElement element = cigar.getCigarElement(indexOfIndel);
		int indelLength = element.getLength();

		int i;
		int refBaseCount = 0, readBaseCount = 0;
		for (i = 0; i < indexOfIndel; i++) {
			CigarElement ce = cigar.getCigarElement(i);
			switch (ce.getOperator()) {
			case X:
			case EQ:
			case M:
				refBaseCount += ce.getLength();
				readBaseCount += ce.getLength();
				break;
			case S:
				readBaseCount += ce.getLength();
				break;
			case N:
				refBaseCount += ce.getLength();
				break;
			default:
				break;
			}
		}

		/*CigarOperator.I needn't be changed*/
		if (element.getOperator() == CigarOperator.D
				&& (indelLength + refBaseCount > ref.length)) {
			indelLength -= (indelLength + refBaseCount - ref.length);
		}

		int refLength = ref.length
				+ (indelLength * (element.getOperator() == CigarOperator.D ? -1
						: 1));
		byte[] alt = new byte[refLength];

		if (refBaseCount > alt.length || refIndex > ref.length)
			return null;

		System.arraycopy(ref, refIndex, alt, 0, refBaseCount);
		int currPos = refBaseCount;

		if (element.getOperator() == CigarOperator.D) {
			refBaseCount += indelLength;
		} else {
			System.arraycopy(read, readBaseCount, alt, currPos, indelLength);
			currPos += indelLength;
		}

		System.arraycopy(ref, refBaseCount, alt, currPos, ref.length
				- refBaseCount);

		return alt;
	}

	public static Cigar leftAlignIndel(Cigar originCigar, final byte[] refSeq,
			final byte[] readSeq, final int refIndex, final int readIndex) {
		Cigar unclipcigar = GaeaCigar.unclipCigar(originCigar);

		int indexOfIndel = GaeaCigar.firstIndexOfIndel(unclipcigar);

		if (indexOfIndel < 1)
			return unclipcigar;

		final int indelLength = unclipcigar.getCigarElement(indexOfIndel)
				.getLength();

		byte[] alt = createStringByIndel(unclipcigar, indexOfIndel, refSeq,
				readSeq, refIndex, readIndex);
		if (alt == null)
			return unclipcigar;

		Cigar newCigar = unclipcigar;
		for (int i = 0; i < indelLength; i++) {
			newCigar = GaeaCigar.moveCigarLeft(newCigar, indexOfIndel, 1);

			if (newCigar == null)
				return unclipcigar;

			byte[] newAlt = createStringByIndel(newCigar, indexOfIndel, refSeq,
					readSeq, refIndex, readIndex);

			boolean reachedEndOfRead = GaeaCigar
					.cigarHasZeroSizeElement(newCigar);

			if (Arrays.equals(alt, newAlt)) {
				unclipcigar = newCigar;
				i = -1;
				if (reachedEndOfRead)
					unclipcigar = GaeaCigar.cleanCigar(unclipcigar);
			}

			if (reachedEndOfRead)
				break;
		}

		return unclipcigar;
	}
}