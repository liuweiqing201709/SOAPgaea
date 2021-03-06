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
package org.bgi.flexlab.gaea.tools.annotator;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import org.bgi.flexlab.gaea.tools.annotator.config.Config;
import org.bgi.flexlab.gaea.tools.annotator.interval.Chromosome;
import org.bgi.flexlab.gaea.tools.annotator.interval.Genome;
import org.bgi.flexlab.gaea.tools.annotator.interval.Variant;
import org.bgi.flexlab.gaea.tools.annotator.realignment.VcfRefAltAlign;

import java.util.*;

public class VcfAnnoContext {
    private String contig;
    private String refStr;
    private int start;
    private int end;
    private List<String> alts;
    protected LinkedList<Variant> variants;
    private List<AnnotationContext> annotationContexts;
    private Map<String, SampleAnnotationContext> sampleAnnoContexts;
    private Map<String, String> annoItems;
    private String annoStr;

    public VcfAnnoContext(){
        variants = new LinkedList<>();
        sampleAnnoContexts = new HashMap<>();
    }

    public VcfAnnoContext(VariantContext variantContext){
        variants = new LinkedList<>();
        sampleAnnoContexts = new HashMap<>();
        init(variantContext);
    }

    public void init(VariantContext variantContext){
        contig = variantContext.getContig();
        refStr = variantContext.getReference().getBaseString();
        start = variantContext.getStart();
        end = variantContext.getEnd();
        alts = new ArrayList<>();
        for (Allele allele : variantContext.getAlternateAlleles()) {
            alts.add(allele.toString());
        }
        addSampleContext(variantContext);

    }

    public void add(VariantContext variantContext){
        for(Allele allele: variantContext.getAlternateAlleles()){
            if(alts.contains(allele.getBaseString()))
                continue;
            alts.add(allele.getBaseString());
        }
        addSampleContext(variantContext);
    }


    private void addSampleContext(VariantContext variantContext){
        for(String sampleName : variantContext.getSampleNamesOrderedByName())
        {
            Genotype gt = variantContext.getGenotype(sampleName);
            if(isVar(gt)){
                if(hasSample(sampleName)){
                    SampleAnnotationContext sampleAnnoContext = sampleAnnoContexts.get(sampleName);
                    List<String> alts = sampleAnnoContext.getAlts();
                    Map<String, Integer> alleleDepths = sampleAnnoContext.getAlleleDepths();
                    Map<String, String> zygosity = sampleAnnoContext.getZygosity();
                    int[] AD = gt.getAD();
                    int i = 0;
                    for(Allele allele: variantContext.getAlternateAlleles()){
                        i++;
                        if(alts.contains(allele.getBaseString()))
                            continue;
                        alts.add(allele.getBaseString());
                        alleleDepths.put(allele.getBaseString(), AD[i]);
                        zygosity.put(allele.getBaseString(), getZygosityType(gt));
                    }
                }else {
                    SampleAnnotationContext sampleAnnoContext = new SampleAnnotationContext(sampleName);
                    Map<String, Integer> alleleDepths = new HashMap<>();
                    Map<String, String> zygosity = new HashMap<>();
    //               判断 gt.hasAD()
                    int[] AD = gt.getAD();
                    int i = 0;
                    List<String> alts = new ArrayList<>();
                    for(Allele allele: variantContext.getAlleles()){
                        alleleDepths.put(allele.getBaseString(), AD[i]);
                        zygosity.put(allele.getBaseString(), getZygosityType(gt));
                        if(i > 0 && AD[i] > 0)
                            alts.add(allele.getBaseString());
                        i++;
                    }
                    sampleAnnoContext.setAlleleDepths(alleleDepths);
                    sampleAnnoContext.setDepth(gt.getDP());
                    sampleAnnoContext.setAlts(alts);
                    sampleAnnoContext.setZygosity(zygosity);
                    sampleAnnoContexts.put(sampleName, sampleAnnoContext);
                }
            }
        }
    }

    private String getZygosityType(Genotype gt){
        if(gt.isHet())
            return "het-ref";
        else if(gt.isHomVar())
            return "hom-alt";
        else if(gt.isHetNonRef())
            return  "het-alt";
        return ".";
    }

    public boolean hasSample(String sampleName){
        return sampleAnnoContexts.containsKey(sampleName);
    }

    /**
     * Create a list of variants from this variantContext
     */
    public List<Variant> variants(Genome genome) {
        if (!variants.isEmpty()) return variants;
        Chromosome chr = genome.getChromosome(contig);

        // interval 使用 0-base 方式建立，应使用start - 1创建variant对象
        for (String alt : alts) {
            Variant variant = createVariant(chr, (int)start - 1, refStr, alt, "");
            variants.add(variant);
        }
        return variants;
    }

    /**
     * Create a variant
     */
    Variant createVariant(Chromosome chromo, int start, String reference, String alt, String id) {
        Variant variant = null;
        if (alt != null) alt = alt.toUpperCase();

        if (alt == null || alt.isEmpty() || alt.equals(reference)) {
            // Non-variant
            variant = Variant.create(chromo, start, reference, null, id);
        } else if (alt.charAt(0) == '<') {
            // TODO Structural variants
            System.err.println("Cann't annotate Structural variants! ");
        } else if ((alt.indexOf('[') >= 0) || (alt.indexOf(']') >= 0)) {
            // TODO Translocations
            System.err.println("Cann't annotate Translocations: ALT has \"[\" or \"]\" info!");

        } else if (reference.length() == alt.length()) {
            // Case: SNP, MNP
            if (reference.length() == 1) {
                // SNPs
                // 20     3 .         C      G       .   PASS  DP=100
                variant = Variant.create(chromo, start, reference, alt, id);
            } else {
                // MNPs
                // 20     3 .         TC     AT      .   PASS  DP=100
                // Sometimes the first bases are the same and we can trim them
                int startDiff = Integer.MAX_VALUE;
                for (int i = 0; i < reference.length(); i++)
                    if (reference.charAt(i) != alt.charAt(i)) startDiff = Math.min(startDiff, i);

                // MNPs
                // Sometimes the last bases are the same and we can trim them
                int endDiff = 0;
                for (int i = reference.length() - 1; i >= 0; i--)
                    if (reference.charAt(i) != alt.charAt(i)) endDiff = Math.max(endDiff, i);

                String newRef = reference.substring(startDiff, endDiff + 1);
                String newAlt = alt.substring(startDiff, endDiff + 1);
                variant = Variant.create(chromo, start + startDiff, newRef, newAlt, id);
            }
        } else {
            // Short Insertions, Deletions or Mixed Variants (substitutions)
            VcfRefAltAlign align = new VcfRefAltAlign(alt, reference);
            align.align();
            int startDiff = align.getOffset();

            switch (align.getVariantType()) {
                case DEL:
                    // Case: Deletion
                    // 20     2 .         TC      T      .   PASS  DP=100
                    // 20     2 .         AGAC    AAC    .   PASS  DP=100
                    String ref = "";
                    String ch = align.getAlignment();
                    if (!ch.startsWith("-")) throw new RuntimeException("Deletion '" + ch + "' does not start with '-'. This should never happen!");
                    variant = Variant.create(chromo, start + startDiff, ref, ch, id);
                    break;

                case INS:
                    // Case: Insertion of A { tC ; tCA } tC is the reference allele
                    // 20     2 .         TC      TCA    .   PASS  DP=100
                    ch = align.getAlignment();
                    ref = "";
                    if (!ch.startsWith("+")) throw new RuntimeException("Insertion '" + ch + "' does not start with '+'. This should never happen!");
                    variant = Variant.create(chromo, start + startDiff, ref, ch, id);
                    break;

                case MIXED:
                    // Case: Mixed variant (substitution)
                    reference = reference.substring(startDiff);
                    alt = alt.substring(startDiff);
                    variant = Variant.create(chromo, start + startDiff, reference, alt, id);
                    break;

                default:
                    // Other change type?
                    throw new RuntimeException("Unsupported VCF change type '" + align.getVariantType() + "'\n\tRef: " + reference + "'\n\tAlt: '" + alt + "'\n\tVcfEntry: " + this);
            }
        }

        //---
        // Add original 'ALT' field as genotype
        //---
        if (variant == null) return null;
        variant.setGenotype(alt);

        return variant;
    }

    public boolean isVar(Genotype gt){
        return  gt.isCalled() && !gt.isHomRef();
    }

    public List<String> getAlts() {
        return alts;
    }

    public Set<String> getGenes() {
        Set<String> genes = new HashSet<>();
        for (AnnotationContext ac : annotationContexts) {
            if (!ac.getGeneName().equals("")) {
                genes.add(ac.getGeneName());
            }
        }
        return genes;
    }

    public List<AnnotationContext> getAnnotationContexts() {
        return annotationContexts;
    }

    public void setAnnotationContexts(List<AnnotationContext> annotationContexts) {
        this.annotationContexts = annotationContexts;
    }


    public String getContig() {
        return contig;
    }

    public void setContig(String contig) {
        this.contig = contig;
    }

    public String getRefStr() {
        return refStr;
    }

    public void setRefStr(String refStr) {
        this.refStr = refStr;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public void setAlts(List<String> alts) {
        this.alts = alts;
    }

    public Map<String, SampleAnnotationContext> getSampleAnnoContexts() {
        return sampleAnnoContexts;
    }

    public void setSampleAnnoContexts(Map<String, SampleAnnotationContext> sampleAnnoContexts) {
        this.sampleAnnoContexts = sampleAnnoContexts;
    }

    public String getChromeNoChr(){
        if(getContig().startsWith("chr")){
            return getContig().substring(3);
        }
        return getContig();
    }
    public String getChrome(){
        if(!getContig().startsWith("chr")){
            return "chr"+getContig();
        }
        return getContig();
    }

    public String getAnnoStr() {
        return annoStr;
    }

    public Map<String, String> getAnnoItems() {
        return annoItems;
    }

    public String getAnnoItem(String key) {
        if(!annoItems.containsKey(key))
            return ".";
        return annoItems.get(key);
    }

    public void parseAnnotationStrings(String annoLine, List<String> header){
        annoItems = new HashMap<>();
        String[] fields = annoLine.split("\tSI:;");
        annoStr = fields[0];
        String[] annoFields = annoStr.split("\t");
        int i = 0;
        for(String k: header){
            annoItems.put(k,annoFields[i]);
            i ++;
        }

        if(!fields[1].equals("")){
            for(String sampleInfo: fields[1].split(";")){
                SampleAnnotationContext sac = new SampleAnnotationContext();
                sac.parseAlleleString(sampleInfo);
                sampleAnnoContexts.put(sac.getSampleName(), sac);
            }
        }
    }

    public List<String> toAnnotationStrings(Config config) {
        List<String> annoStrings = new ArrayList<>();
        for(AnnotationContext ac : annotationContexts){
            StringBuilder sb = new StringBuilder();
            sb.append(getContig());
            sb.append("\t");
            sb.append(start);
            sb.append("\t");
            sb.append(start-1);
            sb.append("\t");
            sb.append(end);
            sb.append("\t");
            sb.append(refStr);
            sb.append("\t");
            sb.append(ac.getAllele());
            ArrayList<String> fields = config.getFieldsByDB(Config.KEY_GENE_INFO);
            for (String field : fields) {
                sb.append("\t");
                if(!ac.getFieldByName(field).isEmpty()){
                    sb.append(ac.getFieldByName(field));
                }else {
                    sb.append(".");
                }
            }

            List<String> dbNameList = config.getDbNameList();
            for (String dbName : dbNameList) {
                fields = config.getFieldsByDB(dbName);
                for (String field : fields) {
                    sb.append("\t");
//						System.err.println("getNumAnnoItems:"+annoContext.getNumAnnoItems());
                    sb.append(ac.getAnnoItemAsString(field, "."));
                }
            }

            sb.append("\tSI:");
            for(SampleAnnotationContext sac: sampleAnnoContexts.values()){
                if(sac.hasAlt(ac.getAllele())){
                    sb.append(";");
                    sb.append(sac.toAlleleString(ac.getAllele()));
                };
            }
            annoStrings.add(sb.toString());
        }
        return annoStrings;
    }
}
