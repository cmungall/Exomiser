/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.charite.compbio.exomiser.core.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk7.Jdk7Module;
import de.charite.compbio.exomiser.core.model.ExomiserSettings;
import de.charite.compbio.exomiser.core.model.SampleData;
import de.charite.compbio.exomiser.core.model.VariantEvaluation;
import de.charite.compbio.exomiser.core.filter.FilterFactory;
import de.charite.compbio.exomiser.core.filter.FilterReport;
import de.charite.compbio.exomiser.core.filter.FilterReportFactory;
import de.charite.compbio.exomiser.core.filter.FilterType;
import de.charite.compbio.exomiser.priority.Priority;
import jannovar.exome.VariantTypeCounter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateMode;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolver;

/**
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public class HtmlResultsWriter implements ResultsWriter {

    private static final Logger logger = LoggerFactory.getLogger(HtmlResultsWriter.class);

    private static TemplateEngine templateEngine;
    
    private final FilterReportFactory filterReportFactory;

    private static final OutputFormat OUTPUT_FORMAT = OutputFormat.HTML;
    
    public HtmlResultsWriter() {
        TemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML5);
        templateResolver.setPrefix("html/views/");
        templateResolver.setSuffix(".html");
        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        filterReportFactory = new FilterReportFactory();
    }

    @Override
    public void writeFile(SampleData sampleData, ExomiserSettings settings, List<Priority> priorityList) {

        String outFileName = ResultsWriterUtils.determineFileExtension(settings.getOutFileName(), OUTPUT_FORMAT);
        Path outFile = Paths.get(outFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(outFile, Charset.defaultCharset())) {

            writer.write(writeString(sampleData, settings, priorityList));

        } catch (IOException ex) {
            logger.error("Unable to write results to file {}.", outFileName, ex);
        }
        logger.info("{} results written to file {}.", OUTPUT_FORMAT, outFileName);

    }

    @Override
    public String writeString(SampleData sampleData, ExomiserSettings settings, List<Priority> priorityList) {
        Context context = new Context();
        //write the settings
        ObjectMapper mapper = new ObjectMapper();
        //required for correct output of Path types
        mapper.registerModule(new Jdk7Module());
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        String jsonSettings = "";
        try {
            jsonSettings = mapper.writeValueAsString(settings);
        } catch (JsonProcessingException ex) {
            logger.error("Unable to process JSON settings", ex);
        }
        context.setVariable("settings", jsonSettings);

        //write out the filter reports section
        List<FilterReport> filterReports = makeFilterReports(settings, sampleData);
        context.setVariable("filterReports", filterReports);
        //write out the variant type counters
        VariantTypeCounter vtc = getVariantTypeCounter(sampleData.getVariantEvaluations());
        //TODO: make this simpler for templating engine to process.
        context.setVariable("variantTypeCounter", vtc);
        
        return templateEngine.process("results", context);
    }

    /**
     * Make a {@code VariantTypeCounter} object from the
     * list of {@code VariantEvaluation}.
     * We use this to print out a table of variant class distribution.
     * @param variantEvaluations
     * @return 
     */
    public VariantTypeCounter getVariantTypeCounter(List<VariantEvaluation> variantEvaluations) {
        
        if (variantEvaluations.isEmpty()) {
            return new VariantTypeCounter(0);
        }
        
        int numIndividuals = variantEvaluations.get(0).getNumberOfIndividuals();
        VariantTypeCounter vtypeCounter = new VariantTypeCounter(numIndividuals);

        for (VariantEvaluation variantEvaluation : variantEvaluations) {
            vtypeCounter.incrementCount(variantEvaluation.getVariant());
        }
        return vtypeCounter;
    }

    protected List<FilterReport> makeFilterReports(ExomiserSettings settings, SampleData sampleData) {

        List<FilterType> filtersApplied = FilterFactory.determineFilterTypesToRun(settings);
        return filterReportFactory.makeFilterReports(filtersApplied, settings, sampleData);
        
    }

}