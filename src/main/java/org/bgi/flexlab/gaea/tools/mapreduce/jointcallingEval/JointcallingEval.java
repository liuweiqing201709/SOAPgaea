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
package org.bgi.flexlab.gaea.tools.mapreduce.jointcallingEval;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.bgi.flexlab.gaea.framework.tools.mapreduce.BioJob;
import org.bgi.flexlab.gaea.framework.tools.mapreduce.ToolsRunner;
import org.bgi.flexlab.gaea.tools.mapreduce.annotator.*;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Created by huangzhibo on 2017/12/12.
 */
public class JointcallingEval extends ToolsRunner {

    public JointcallingEval(){}

    public int runJointcalingEval(String[] arg0) throws Exception {

        Configuration conf = new Configuration();
        String[] remainArgs = remainArgs(arg0, conf);

        JointcallingEcalOptions options = new JointcallingEcalOptions();
        options.parse(remainArgs);
        options.setHadoopConf(remainArgs, conf);
        BioJob job = BioJob.getInstance(conf);

//        job.setHeader(new Path(options.getInput()), new Path(options.getOutputPath()));
        job.setJobName("GaeaJointcalingEval");
        job.setJarByClass(this.getClass());
        job.setMapperClass(JointcallingEvalMapper.class);
        job.setReducerClass(JointcallingEvalReducer.class);
        job.setNumReduceTasks(options.getReducerNum());

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(VcfLineWritable.class);


        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.setInputFormatClass(MNLineInputFormat.class);

        MNLineInputFormat.addInputPath(job, new Path(options.getInputFilePath()));
        MNLineInputFormat.addInputPath(job, new Path(options.getBaselineFile()));
        MNLineInputFormat.setMinNumLinesToSplit(job,1000); //按行处理的最小单位
        MNLineInputFormat.setMapperNum(job, options.getReducerNum());

//        Path partTmp = new Path(options.getTmpPath());
        Path outputPath = new Path(options.getOutputTmpPath());

        FileOutputFormat.setOutputPath(job, outputPath);
//        FileOutputFormat.setOutputPath(job, partTmp);
        if (job.waitForCompletion(true)) {
                final FileStatus[] parts = outputPath.getFileSystem(conf).globStatus(new Path(options.getOutputTmpPath() +
                        "/part" + "-*-[0-9][0-9][0-9][0-9][0-9]*"));
                GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(options.getOutputPath() + "/report.tsv.gz"));

                List<String> statKey = new ArrayList<>();
                Map<String, int[]> stat = new HashMap<>();
                boolean writeHeader = true;
                for (FileStatus p : parts) {
                    FSDataInputStream dis = p.getPath().getFileSystem(conf).open(p.getPath());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(dis));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("#")) {
                            if (writeHeader) {
                                os.write(line.getBytes());
                                os.write('\n');
                                writeHeader = false;
                            }
                            continue;
                        }
                        int[] statResult;
                        String[] fields = line.split("\t");
                        if(stat.containsKey(fields[0]))
                            statResult = stat.get(fields[0]);
                        else
                            statResult = new int[3];
                        for(int i=1; i<4; i++){
                            statResult[i-1] += Integer.parseInt(fields[i]);
                        }
                        if (!statKey.contains(fields[0]))
                            statKey.add(fields[0]);
                        stat.put(fields[0],statResult);
                    }
                }

                for(String key: statKey){
                    int[] statResult = stat.get(key);
                    String result = key +"\t" + statResult[0] +"\t" + statResult[1] +"\t"+ statResult[2];
                    os.write(result.getBytes());
                    os.write('\n');
                }
                os.close();
//                partTmp.getFileSystem(conf).delete(partTmp, true);
            return 0;
        }else {
            return 1;
        }
    }


    @Override
    public int run(String[] args) throws Exception {
        JointcallingEval md = new JointcallingEval();
        return md.runJointcalingEval(args);
    }

}
