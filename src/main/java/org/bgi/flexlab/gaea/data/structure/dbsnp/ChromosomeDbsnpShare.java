package org.bgi.flexlab.gaea.data.structure.dbsnp;

import org.bgi.flexlab.gaea.data.structure.memoryshare.BioMemoryShare;
import org.bgi.flexlab.gaea.data.structure.reference.index.VcfIndex;

public class ChromosomeDbsnpShare extends BioMemoryShare {
	private final static int WINDOW_SIZE = VcfIndex.WINDOW_SIZE;
	private final static int CAPACITY = Long.SIZE / Byte.SIZE;

	public ChromosomeDbsnpShare() {
		super(1);
	}

	public long getStartPosition(int winNum, int winSize) {
		if (winNum * winSize >= getLength())
			throw new RuntimeException("position is more than chromosome length.");

		if (winSize == 0)
			winSize = WINDOW_SIZE;

		if (winSize % WINDOW_SIZE != 0)
			throw new RuntimeException("window size is not multiple for " + WINDOW_SIZE);

		int multipe = winSize / WINDOW_SIZE;

		int minWinNum = winNum * multipe;
		int maxWinNum = (winNum + 1) * multipe;
		
		if(minWinNum * CAPACITY >= fcSize)
			return -1;
		int end = maxWinNum * CAPACITY - 1;
		if(end >= fcSize)
			end = (fcSize - 1);
			
		byte[] indexs = getBytes(minWinNum * CAPACITY, end);

		long position = 0;

		for (int j = 0; j < indexs.length; j += CAPACITY) {
			for (int i = 0; i < CAPACITY; i++) {
				position <<= CAPACITY;
				position |= (indexs[j + i] & 0xff);
			}
			if (position != 0)
				return position;

			position = 0;
		}

		return -1;
	}

	public long getStartPosition(int winNum) {
		return getStartPosition(winNum, WINDOW_SIZE);
	}
}
