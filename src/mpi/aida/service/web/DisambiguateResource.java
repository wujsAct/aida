package mpi.aida.service.web;

import mpi.aida.Disambiguator;
import mpi.aida.Preparator;
import mpi.aida.config.settings.DisambiguationSettings;
import mpi.aida.config.settings.JsonSettings.JSONTYPE;
import mpi.aida.config.settings.PreparationSettings;
import mpi.aida.config.settings.disambiguation.CocktailPartyDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.CocktailPartyKOREDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.CocktailPartyWithHeuristicsDisambiguationWithNullSettings;
import mpi.aida.config.settings.disambiguation.LocalDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.PriorOnlyDisambiguationSettings;
import mpi.aida.config.settings.preparation.StanfordHybridPreparationSettings;
import mpi.aida.data.DisambiguationResults;
import mpi.aida.data.PreparedInput;
import mpi.aida.data.ResultProcessor;
import mpi.aida.preparation.mentionrecognition.FilterMentions.FilterType;
import mpi.aida.service.web.logger.WebCallLogger;

import org.json.simple.JSONObject;

/*
 * A simple class to disambiguate the input text received by Requestprocessor.
 */
public class DisambiguateResource {
  
	private final PreparationSettings prepSettings;
	private DisambiguationSettings disSettings;

	// private Settings settings;

	public DisambiguateResource(String technique, boolean isManual)
			throws Exception {
	  prepSettings = new StanfordHybridPreparationSettings();
		disSettings = null;
		if (technique.equals("PRIOR")) {
      disSettings = new PriorOnlyDisambiguationSettings();
    } else if (technique.equals("LOCAL")) {
			disSettings = new LocalDisambiguationSettings();
		} else if (technique.equals("GRAPH")) {
			disSettings = new CocktailPartyDisambiguationSettings();
		} else if (technique.equals("GRAPH-KORE")) {
			disSettings = new CocktailPartyKOREDisambiguationSettings();
		} else if (technique.equals("GRAPH-NULL")) {
      disSettings = new CocktailPartyWithHeuristicsDisambiguationWithNullSettings();
    }
		
		if (isManual)
			prepSettings.setMentionsFilter(FilterType.Manual);
	}

	/**
	 * Responsible for disambiguating the given input string.
	 * 
	 * @param input Input string to disambiguate
	 * @return JSON results
	 */
	public String process(String input) throws Exception {
	  return process(input, null);
	}

  /**
   * Responsible for disambiguating the given input string.
   * 
   * @param input Input string to disambiguate.
   * @param callerIP IP address of the caller for logging.
   * @return JSON results
   */
	public String process(String input, String callerIP) throws Exception {
	  long start = System.currentTimeMillis();
	  
		Preparator p = new Preparator();
		PreparedInput preInput = p.prepare(
				String.valueOf(input.hashCode() + System.currentTimeMillis()),
				input, prepSettings);

		DisambiguationResults results = null;
		String jsonStr;

		Disambiguator d = new Disambiguator(preInput, disSettings);
		results = d.disambiguate();
		ResultProcessor rp = new ResultProcessor(input, results, null,
				preInput, 5);
		JSONObject result = rp.process(JSONTYPE.EXTENDED);
		jsonStr = result.toJSONString();
				
		long dur = System.currentTimeMillis() - start;

		// log call details
		RequestLogger.logProcess(callerIP, preInput, prepSettings.getClass().getName(), 
		    disSettings.getDisambiguationTechnique(), 
		    disSettings.getDisambiguationAlgorithm(), dur);
		WebCallLogger.log(input, jsonStr, prepSettings.getClass().getName(), disSettings.getDisambiguationTechnique().toString(), disSettings.getDisambiguationAlgorithm().toString());
		return jsonStr;
	}
	
	public DisambiguationResults disambiguate(String input) throws Exception {
		Preparator p = new Preparator();
		PreparedInput preInput = p.prepare(
				String.valueOf(input.hashCode() + System.currentTimeMillis()),
				input, prepSettings);

		DisambiguationResults results = null;
		Disambiguator d = new Disambiguator(preInput, disSettings);
		results = d.disambiguate();
		return results;
	}
}