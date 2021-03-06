package org.bgi.flexlab.gaea.tools.haplotypecaller.argumentcollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bgi.flexlab.gaea.tools.haplotypecaller.ReferenceConfidenceMode;
import org.bgi.flexlab.gaea.tools.haplotypecaller.annotation.StandardHCAnnotation;
import org.bgi.flexlab.gaea.tools.haplotypecaller.smithwaterman.SmithWatermanAligner;
import org.bgi.flexlab.gaea.tools.haplotypecaller.smithwaterman.SmithWatermanAligner.Implementation;
import org.bgi.flexlab.gaea.tools.haplotypecaller.writer.HaplotypeBAMWriter;
import org.bgi.flexlab.gaea.tools.jointcalling.genotypegvcfs.annotation.StandardAnnotation;

import htsjdk.variant.variantcontext.VariantContext;;

public class HaplotypeCallerArgumentCollection extends StandardCallerArgumentCollection{

	public AssemblyRegionTrimmerArgumentCollection assemblyRegionTrimmerArgs = new AssemblyRegionTrimmerArgumentCollection();

    public ReadThreadingAssemblerArgumentCollection assemblerArgs = new ReadThreadingAssemblerArgumentCollection();

    public LikelihoodEngineArgumentCollection likelihoodArgs = new LikelihoodEngineArgumentCollection();

    /**
     * rsIDs from this file are used to populate the ID column of the output. Also, the DB INFO flag will be set when appropriate.
     * dbSNP is not used in any way for the calculations themselves.
     */
    public List<VariantContext> dbsnp;

    /**
     * If a call overlaps with a record from the provided comp track, the INFO field will be annotated
     * as such in the output with the track name (e.g. -comp:FOO will have 'FOO' in the INFO field). Records that are
     * filtered in the comp track will be ignored. Note that 'dbSNP' has been special-cased (see the --dbsnp argument).
     */
    public Map<String,List<VariantContext>> comps = null;

    public boolean debug;

    public boolean USE_FILTERED_READ_MAP_FOR_ANNOTATIONS = false;

    /**
     * The reference confidence mode makes it possible to emit a per-bp or summarized confidence estimate for a site being strictly homozygous-reference.
     * See http://www.broadinstitute.org/gatk/guide/article?id=2940 for more details of how this works.
     * Note that if you set -ERC GVCF, you also need to set -variant_index_type LINEAR and -variant_index_parameter 128000 (with those exact values!).
     * This requirement is a temporary workaround for an issue with index compression.
     */
    public ReferenceConfidenceMode emitReferenceConfidence = ReferenceConfidenceMode.NONE;

    /**
     * The assembled haplotypes and locally realigned reads will be written as BAM to this file if requested.  Really
     * for debugging purposes only. Note that the output here does not include uninformative reads so that not every
     * input read is emitted to the bam.
     *
     * Turning on this mode may result in serious performance cost for the caller.  It's really only appropriate to
     * use in specific areas where you want to better understand why the caller is making specific calls.
     *
     * The reads are written out containing an "HC" tag (integer) that encodes which haplotype each read best matches
     * according to the haplotype caller's likelihood calculation.  The use of this tag is primarily intended
     * to allow good coloring of reads in IGV.  Simply go to "Color Alignments By > Tag" and enter "HC" to more
     * easily see which reads go with these haplotype.
     *
     * Note that the haplotypes (called or all, depending on mode) are emitted as single reads covering the entire
     * active region, coming from sample "HC" and a special read group called "ArtificialHaplotype". This will increase the
     * pileup depth compared to what would be expected from the reads only, especially in complex regions.
     *
     * Note also that only reads that are actually informative about the haplotypes are emitted.  By informative we mean
     * that there's a meaningful difference in the likelihood of the read coming from one haplotype compared to
     * its next best haplotype.
     *
     * If multiple BAMs are passed as input to the tool (as is common for M2), then they will be combined in the bamout
     * output and tagged with the appropriate sample names.
     *
     * The best way to visualize the output of this mode is with IGV.  Tell IGV to color the alignments by tag,
     * and give it the "HC" tag, so you can see which reads support each haplotype.  Finally, you can tell IGV
     * to group by sample, which will separate the potential haplotypes from the reads.  All of this can be seen in
     * <a href="https://www.dropbox.com/s/xvy7sbxpf13x5bp/haplotypecaller%20bamout%20for%20docs.png">this screenshot</a>
     *
     */
    public String bamOutputPath = null;

    /**
     * The type of BAM output we want to see. This determines whether HC will write out all of the haplotypes it
     * considered (top 128 max) or just the ones that were selected as alleles and assigned to samples.
     */
    public HaplotypeBAMWriter.WriterType bamWriterType = HaplotypeBAMWriter.WriterType.CALLED_HAPLOTYPES;

    /**
     * If set, certain "early exit" optimizations in HaplotypeCaller, which aim to save compute and time by skipping
     * calculations if an ActiveRegion is determined to contain no variants, will be disabled. This is most likely to be useful if
     * you're using the -bamout argument to examine the placement of reads following reassembly and are interested in seeing the mapping of
     * reads in regions with no variations. Setting the -forceActive and -dontTrimActiveRegions flags may also be necessary.
     */
    public boolean disableOptimizations = false;

    // -----------------------------------------------------------------------------------------------
    // arguments for debugging / developing
    // -----------------------------------------------------------------------------------------------

    public String keepRG = null;

    /**
     * This argument is intended for benchmarking and scalability testing.
     */
    public boolean justDetermineActiveRegions = false;

    /**
     * This argument is intended for benchmarking and scalability testing.
     */
    public boolean dontGenotype = false;

    public boolean dontUseSoftClippedBases = false;

    public boolean captureAssemblyFailureBAM = false;

    // Parameters to control read error correction
    /**
     * Enabling this argument may cause fundamental problems with the assembly graph itself.
     */
    public boolean errorCorrectReads = false;

    public boolean doNotRunPhysicalPhasing = false;

    /**
     * Bases with a quality below this threshold will not be used for calling.
     */
    public byte minBaseQualityScore = 10;

    //Annotations

    public Implementation smithWatermanImplementation = SmithWatermanAligner.Implementation.FASTEST_AVAILABLE;
    
	private static final long serialVersionUID = 1L;

    /**
     * When HaplotypeCaller is run with -ERC GVCF or -ERC BP_RESOLUTION, some annotations are excluded from the
     * output by default because they will only be meaningful once they have been recalculated by GenotypeGVCFs. As
     * of version 3.3 this concerns ChromosomeCounts, FisherStrand, StrandOddsRatio and QualByDepth.
     */
    public VariantAnnotationArgumentCollection variantAnnotationArgumentCollection = new VariantAnnotationArgumentCollection(
            Arrays.asList(StandardAnnotation.class.getSimpleName(), StandardHCAnnotation.class.getSimpleName()),
            Collections.emptyList(),
            Collections.emptyList());

    /**
     * You can use this argument to specify that HC should process a single sample out of a multisample BAM file. This
     * is especially useful if your samples are all in the same file but you need to run them individually through HC
     * in -ERC GVC mode (which is the recommended usage). Note that the name is case-sensitive.
     */
    public String sampleNameToUse = null;

    // -----------------------------------------------------------------------------------------------
    // general advanced arguments to control haplotype caller behavior
    // -----------------------------------------------------------------------------------------------

    /**
     * When HC is run in reference confidence mode with banding compression enabled (-ERC GVCF), homozygous-reference
     * sites are compressed into bands of similar genotype quality (GQ) that are emitted as a single VCF record. See
     * the FAQ documentation for more details about the GVCF format.
     *
     * This argument allows you to set the GQ bands. HC expects a list of strictly increasing GQ values
     * that will act as exclusive upper bounds for the GQ bands. To pass multiple values,
     * you provide them one by one with the argument, as in `-GQB 10 -GQB 20 -GQB 30` and so on
     * (this would set the GQ bands to be `[0, 10), [10, 20), [20, 30)` and so on, for example).
     * Note that GQ values are capped at 99 in the GATK, so values must be integers in [1, 100].
     * If the last value is strictly less than 100, the last GQ band will start at that value (inclusive)
     * and end at 100 (exclusive).
     */
    public List<Integer> GVCFGQBands = new ArrayList<>(70);
    {
            for (int i=1; i<=60; ++i) {
                GVCFGQBands.add(i);
            }
            GVCFGQBands.add(70); GVCFGQBands.add(80); GVCFGQBands.add(90); GVCFGQBands.add(99);
    };

    /**
     * This parameter determines the maximum size of an indel considered as potentially segregating in the
     * reference model.  It is used to eliminate reads from being indel informative at a site, and determines
     * by that mechanism the certainty in the reference base.  Conceptually, setting this parameter to
     * X means that each informative read is consistent with any indel of size < X being present at a specific
     * position in the genome, given its alignment to the reference.
     */
    public int indelSizeToEliminateInRefModel = 10;

    public boolean USE_ALLELES_TRIGGER = false;
}
