package org.bgi.flexlab.gaea.data.structure.region;

import java.util.concurrent.ConcurrentHashMap;

import org.bgi.flexlab.gaea.util.posInfo.IntPosInfo;

public class BedSingleRegionReport extends SingleRegionReport<BedSingleRegionStatistic>{	
	public BedSingleRegionReport(SingleRegion singleReigon) {
		super(singleReigon);
		result = new ConcurrentHashMap<SingleRegion.Regiondata, BedSingleRegionStatistic>();
	}

	@Override
	public String getWholeRegionInfo(IntPosInfo deep, int start, int end) {
		int coverageNum = 0;
		int depthNum = 0;
		
		for(int i = start; i <= end; i++) {
			int deepNum = deep.get(i);
			if(deepNum > 0) {
				coverageNum++;
				depthNum += deepNum;
			}
		}
		
		outputString.append(depthNum);
		outputString.append("\t");
		outputString.append(coverageNum);
		outputString.append("\n");
		
		return outputString.toString();
	}

	@Override
	public String getPartRegionInfo(IntPosInfo deep, int start, int end) {
		return getWholeRegionInfo(deep, start, end);
	}

	@Override
	public void parseReducerOutput(String line, boolean isPart) {
		String[] splits = line.split(":");
		int regionIndex = Integer.parseInt(splits[0]);
		BedSingleRegionStatistic statistic = null;
		if(isPart) {
			updatePartResult(statistic, splits, regionIndex);
		} else {
			updateResult(statistic, splits, regionIndex);
		}
	}
	
	private void updateResult(BedSingleRegionStatistic statistic, String[] splits, int regionIndex) {
		statistic = new BedSingleRegionStatistic();
		statistic.add(splits[1]);
		result.put(singleReigon.getRegion(regionIndex), statistic);
	}
	
	private void updatePartResult(BedSingleRegionStatistic statistic, String[] splits, int regionIndex) {
		if(!result.containsKey(singleReigon.getRegion(regionIndex))) {
			updateResult(statistic, splits, regionIndex);
		} else {
			statistic =  result.get(singleReigon.getRegion(regionIndex));
			statistic.add(splits[1]);
		}
	}
}