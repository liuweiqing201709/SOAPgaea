/*******************************************************************************
 * Copyright (c) 2017, BGI-Shenzhen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *******************************************************************************/
package org.bgi.flexlab.gaea.data.structure.pileup;

import org.bgi.flexlab.gaea.data.structure.alignment.AlignmentsBasic;
import org.bgi.flexlab.gaea.tools.mapreduce.genotyper.GenotyperOptions;

import java.util.ArrayList;

/**
 * Created by zhangyong on 2016/12/26.
 */
public interface PileupInterface<T extends PileupReadInfo> {
    void calculateBaseInfo(GenotyperOptions options);

    void remove();

    void forwardPosition(int size);

    boolean isEmpty();

    void addReads(AlignmentsBasic read);

}
