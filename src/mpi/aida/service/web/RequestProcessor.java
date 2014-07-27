package mpi.aida.service.web;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import mpi.aida.AidaManager;
import mpi.aida.Disambiguator;
import mpi.aida.Preparator;
import mpi.aida.access.DataAccess;
import mpi.aida.config.settings.DisambiguationSettings;
import mpi.aida.config.settings.JsonSettings.JSONTYPE;
import mpi.aida.config.settings.PreparationSettings;
import mpi.aida.config.settings.Settings.ALGORITHM;
import mpi.aida.config.settings.Settings.TECHNIQUE;
import mpi.aida.config.settings.disambiguation.CocktailPartyDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.CocktailPartyKOREDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.FastCocktailPartyDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.FastLocalDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.LocalDisambiguationIDFSettings;
import mpi.aida.config.settings.disambiguation.LocalDisambiguationSettings;
import mpi.aida.config.settings.disambiguation.PriorOnlyDisambiguationSettings;
import mpi.aida.config.settings.preparation.DictionaryBasedNerPreparationSettings;
import mpi.aida.config.settings.preparation.StanfordHybridPreparationSettings;
import mpi.aida.data.DisambiguationResults;
import mpi.aida.data.Entities;
import mpi.aida.data.Entity;
import mpi.aida.data.EntityMetaData;
import mpi.aida.data.KBIdentifiedEntity;
import mpi.aida.data.PreparedInput;
import mpi.aida.data.ResultProcessor;
import mpi.aida.data.Type;
import mpi.aida.graph.similarity.EntityEntitySimilarity;
import mpi.aida.graph.similarity.util.SimilaritySettings;
import mpi.aida.preparation.mentionrecognition.FilterMentions.FilterType;
import mpi.aida.service.web.logger.WebCallLogger;
import mpi.experiment.trace.GraphTracer;
import mpi.experiment.trace.GraphTracer.TracingTarget;
import mpi.experiment.trace.NullTracer;
import mpi.experiment.trace.Tracer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class defining the HTTP interface to call the AIDA disambiugation. Call
 * /service/disambiguate-defaultsettings or /service/disambiguate to call the
 * actual disambiguation service. See the methods for the expected parameters.
 */

@Path("/service")
public class RequestProcessor {

	private static long processCount = 0l;

	static {
		AidaManager.init();
	}

	private static final Logger logger = LoggerFactory
			.getLogger(RequestProcessor.class);

	@Path("/status")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String status() {
		return "Processed " + processCount + " docs.";
	}

	/**
	 * Convenience method to call the AIDA disambiguation service. If you want
	 * to specify all settings, use /disambiguate (see below).
	 * 
	 * Input: HTTP POST request containing application/x-www-form-urlencoded
	 * parameters specifying the settings and input text. See below for expected
	 * values. Output: JSON containing the disambiguation results.
	 * 
	 * @param text
	 *            The input text to disambiguate
	 * @param tech
	 *            The technique to use (LOCAL or GRAPH - default is GRAPH)
	 * @param tag_mode
	 *            Set to 'manual' to give AIDA pre-defined mentions as part of
	 *            the text input (marked with [[..]])
	 */
	@Path("/disambiguate-defaultsettings")
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	public String process(
	    @Context HttpServletRequest req,
	    @FormParam("text") String text,
			@FormParam("tech") String technique,
			@FormParam("tag_mode") String mTagMode) {
		boolean isManual = false;
		
		if (text == null)
			return "MISSING: Text to Disambiguate";

		if (mTagMode != null)
			isManual = mTagMode.equals("manual") ? true : false;
		else
			// default to StanfordNER
			isManual = false;

		if (technique == null)
			technique = "GRAPH";

		try {
			DisambiguateResource dResource = new DisambiguateResource(
					technique, isManual);
			incrememtProcessCount();
			return dResource.process(text, getCallerIp(req));
		} catch (Exception e) {
			e.printStackTrace();
			return "ERROR: Failed Disambiguating";
		}
	}
	
	private String getCallerIp(HttpServletRequest req) {
	   String ip = req.getRemoteAddr();
	   // Make sure to get the actual IP of the requester if 
	   // the service works behind a gateway.
	   String forward = req.getHeader("X-Forwarded-For");
	   if (forward != null) {
	     ip = forward;
	   }
	   return ip;
	}
	
	@Path("/callerinfo")
	@GET
	@Produces(MediaType.TEXT_PLAIN)	
	public String getCallerInfo(@Context HttpServletRequest req) {
	  String remoteHost = req.getRemoteHost();
    String remoteAddr = req.getRemoteAddr();
    int remotePort = req.getRemotePort();
    String forward = req.getHeader("X-Forwarded-For");
    
    String msg = remoteHost + " (" + remoteAddr + ":" + remotePort + ") forward for " + forward;
    return msg;
	}

	/**
	 * Method to call the AIDA disambiguation service.
	 * 
	 * Input: HTTP POST request containing application/x-www-form-urlencoded
	 * parameters specifying the settings and input text. See below for expected
	 * values. Output: JSON containing the disambiguation results.
	 * 
	 * @param text
	 *            The input text to disambiguate
	 * @param type
	 *            The type of the input text (TEXT, TABLE, XML - default is
	 *            TEXT)
	 * @param tag_mode
	 *            Set to 'manual' to give AIDA pre-defined mentions as part of
	 *            the text input (marked with [[..]])
	 * @param doc_id
	 *            Specify the document id.
	 * @param tech
	 *            The technique to use (LOCAL or GRAPH - default is LOCAL)
	 * @param algo
	 *            Algorithm to use when GRAPH is set as tech. Default is CPSC
	 *            (size constrained).
	 * @param alpha
	 *            alpha is multiplied to ME edges, 1-alpha to EE edges (a is in
	 *            [0.0, 1.0])
	 * @param ppWeight
	 *            Weight to balance the prior probability of mention-entity
	 *            pairs and contextual similarity.
	 * @param entities_per_mention
	 *            Number of candidates to use in the CPSC setting.
	 * @param coherence_threshold
	 *            Threshold to use for the coherence robustness test (in [0.0,
	 *            2.0])
	 * @param interface Set to true for the webaida demo.
	 * @param exhaustive_search
	 *            Set to false to not do a exhaustive post-processing after the
	 *            graph algorithm for selecting the correct entity. Default is
	 *            true.
	 * @param fast_mode
	 *            Set to true to cut down on the number of keyphrases used per
	 *            entity candidate, using only the most specific ones. Speeds up
	 *            processing by a factor of 5 with little impact on quality.
	 * @param filtering_types
	 *            Semantic (YAGO) types to restrict the entity candidates to.
	 *            Format: KB:typename,KB:typename,...
	 * @param keyphrasesSourceWeightsStr
	 *            Set by the webaida demo.
	 * @param maxResults
	 *            Number of entity candidates per mention to include in the
	 *            returned JSON object.
	 */
	@Path("/disambiguate")
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	public String processWebRequest(
	    @Context HttpServletRequest req,
			@FormParam("text") String text,
			@FormParam("type") String inputType,
			@FormParam("tag_mode") String tagMode,
			@FormParam("doc_id") String docId,
			@FormParam("tech") String technique,
			@FormParam("algo") String algorithm,
			@FormParam("alpha") double alpha,
			@FormParam("ppWeight") double ppWeight,
			@FormParam("importance_weight") Double importanceWeight,
			@FormParam("entities_per_mention") int ambiguity,
			@FormParam("coherence_treshold") double coherence,
			@FormParam("interface") boolean isWebInterface,
			@FormParam("exhaustive_search") boolean exhaustive_search,
			@FormParam("fast_mode") boolean fastMode,
			@FormParam("filtering_types") String filteringTypes,
			@FormParam("keyphrasesSourceWeightsStr") String keyphrasesSourceWeightsStr,
			@FormParam("maxResults") Integer maxResults,
			@FormParam("nullMappingThreshold") Double nullMappingThreshold)
			throws Exception {
		long time = System.currentTimeMillis();

		// 2. generate preparedSettings and set all required parameters
		// including filter types
		PreparationSettings prepSettings;

		if (tagMode != null) {
			if (tagMode.equals("stanfordNER")) {
				prepSettings = new StanfordHybridPreparationSettings();
			} else if (tagMode.equals("dictionary")) {
				prepSettings = new DictionaryBasedNerPreparationSettings();
			} else { // if (tagMode.equals("manual"))
				prepSettings = new PreparationSettings();
				prepSettings.setMentionsFilter(FilterType.Manual);
			}
		} else {
			// default to manual if tag mode not used
			prepSettings = new PreparationSettings();
			prepSettings.setMentionsFilter(FilterType.Manual);
		}

		if (filteringTypes != null && !filteringTypes.equals("")) {
			String[] typesStrings;
			typesStrings = filteringTypes.split(",");
			Type[] types = new Type[typesStrings.length];
			int count = 0;
			for (String typeString : typesStrings) {
				int colonIndex = typeString.indexOf(":");
				String kb = typeString.substring(0, colonIndex);
				String typeName = typeString.substring(colonIndex + 1);
				types[count++] = new Type(kb, typeName);
			}

			prepSettings.setFilteringTypes(types);

		} else {
			prepSettings.setFilteringTypes(null);
		}

		// 3a. generate disambiguateSettings and define all the parameters
		DisambiguationSettings disSettings = null;
		if (technique.equals("PRIOR")) {
			disSettings = new PriorOnlyDisambiguationSettings();
		} else if (technique.equals("LOCAL")) {
			if (fastMode)
				disSettings = new FastLocalDisambiguationSettings();
			else
				disSettings = new LocalDisambiguationSettings();
		} else if (technique.equals("LOCAL-IDF")) {
			disSettings = new LocalDisambiguationIDFSettings();
		} else if (technique.equals("GRAPH")) {
			if (fastMode)
				disSettings = new FastCocktailPartyDisambiguationSettings();
			else
				disSettings = new CocktailPartyDisambiguationSettings();
			if (algorithm != null) {
				if (algorithm.equalsIgnoreCase("cpsc"))
					disSettings
							.setDisambiguationAlgorithm(ALGORITHM.COCKTAIL_PARTY_SIZE_CONSTRAINED);
				// else if(algorithm.equalsIgnoreCase("random"))
				// disSettings.setDisambiguationAlgorithm(ALGORITHM.RANDOM_WALK);
			}		
		} else if (technique.equals("GRAPH-KORE")) {
			disSettings = new CocktailPartyKOREDisambiguationSettings();		
		} else {
			// TODO return something that makes sense.. like a json with error
			// code
			return null;
		}
		// 3b. set disambiguation parameters
		disSettings.getGraphSettings().setAlpha(alpha);
		disSettings.getGraphSettings().setEntitiesPerMentionConstraint(
				ambiguity);
		disSettings.getGraphSettings().setCohRobustnessThreshold(coherence);
		Tracer tracer = null;
		if (isWebInterface) {
			disSettings.setTracingTarget(TracingTarget.WEB_INTERFACE);
			GraphTracer.gTracer = new GraphTracer();
			if (technique.equals("PRIOR") || technique.equals("LOCAL")
					|| technique.equals("LOCAL-IDF")) {
				tracer = new Tracer(docId);
			} else {
				tracer = new NullTracer();
			}
		} else {
			tracer = new NullTracer();
		}
		// 4a. make sure to update similarity settings with prior weight and/or
		// importance weight
		SimilaritySettings simSettings = disSettings.getSimilaritySettings();
		if (disSettings.getDisambiguationTechnique() == TECHNIQUE.GRAPH)
			adjustSimSettingsForNewPriorWeight(simSettings, ppWeight);

		if (importanceWeight != null) {
			// TODO mamir,jhoffart: switched off for now, need to get it back
			// and adjust it work for
			// multiple importances if needed
			// adjustSimSettingsForNewImportanceWeight(simSettings,
			// importanceWeight);
		}

		disSettings.getGraphSettings()
				.setUseExhaustiveSearch(exhaustive_search);
		if (nullMappingThreshold != null) {
			disSettings.setNullMappingThreshold(nullMappingThreshold);
		}

		// 4b. set keyphrases sources weights passed from the UI
		if (keyphrasesSourceWeightsStr != null
				&& !keyphrasesSourceWeightsStr.equals("")) {
			List<String[]> mentionEntityKeyphraseSourceWeightsList = buildKeyphrasesSourcesWeights(keyphrasesSourceWeightsStr);
			simSettings
					.setMentionEntityKeyphraseSourceWeights(mentionEntityKeyphraseSourceWeightsList);
		}

		// 4c. prepare input
		if (docId == null) {
			docId = "" + System.currentTimeMillis();
		}

		Preparator p = new Preparator();
		PreparedInput preInput = p.prepare(docId, text, prepSettings);

		// 5. disambiguator instantiated and run disambiguate
		Disambiguator disambiguator = new Disambiguator(preInput, disSettings,
				tracer);
		DisambiguationResults results = disambiguator.disambiguate();
		incrememtProcessCount();

		// 6. resultprocessor instantiated and json retrieved from results
		// based on the interface json returned will have HTML string / JSON
		// repr of Disambi Results

		int maxNum = 15;
		if (maxResults != null) {
			maxNum = maxResults;
		}
		ResultProcessor rp = new ResultProcessor(text, results, null, preInput,
				maxNum);
		long duration = System.currentTimeMillis() - time;
		rp.setOverallTime(duration);
		JSONObject json;
		if (isWebInterface) {
			json = rp.process(JSONTYPE.WEB);
		} else {
			json = rp.process(JSONTYPE.COMPACT);
		}
		
		// log request details
		RequestLogger.logProcess(getCallerIp(req), preInput, 
		    prepSettings.getClass().getName(), 
		    disSettings.getDisambiguationTechnique(), 
		    disSettings.getDisambiguationAlgorithm(), duration);
		WebCallLogger.log(text, json.toJSONString(), prepSettings.getClass().getName(), technique, algorithm);
		return json.toJSONString();
	}

	@SuppressWarnings("unchecked")
	@Path("/loadKeyphraseWeights")
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	public String processSettingsRequest() {
		// this will load settings from DB
		Map<String, Double> hshWeights = DataAccess.getKeyphraseSourceWeights();
		JSONObject jObj = new JSONObject();
		for (Entry<String, Double> e : hshWeights.entrySet()) {
			jObj.put(e.getKey(), e.getValue());
		}

		return jObj.toJSONString();
	}

	@SuppressWarnings("unchecked")
	@Path("/loadEntityMetaData")
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	public String loadEntityMetaData(@FormParam("entity") Integer entity) {
		EntityMetaData entityMetaData = DataAccess.getEntityMetaData(entity);
		double importance = DataAccess.getEntityImportance(entity);
		JSONObject jObj = new JSONObject();
		jObj.put("readableForm",
				entityMetaData.getHumanReadableRepresentation());
		jObj.put("url", entityMetaData.getUrl());
		jObj.put("importance", importance);
		jObj.put("knowledgebase", entityMetaData.getKnowledgebase());
		jObj.put("depictionurl", entityMetaData.getDepictionurl());
		jObj.put("depictionthumbnailurl",
				entityMetaData.getDepictionthumbnailurl());
		return jObj.toJSONString();
	}

	// for the webaida entity.jsp page
	@Path("/loadKeyphrases")
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	public String loadKeyphrases(@FormParam("entity") Integer entity) {
		JSONArray keyphrasesJsonArray = EntityDetailsLoader
				.loadKeyphrases(entity);
		// JSONArray entityTypesJsonArray =

		return keyphrasesJsonArray.toJSONString();
	}

	// for the webaida keyphrases.jsp page
	@Path("/loadTypes")
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	public String loadTypes(@FormParam("entity") int entity) {
		JSONArray typesJsonArray = EntityDetailsLoader.loadEntityTypes(entity);
		return typesJsonArray.toJSONString();
	}

	@SuppressWarnings("unchecked")
	@Path("/computeMilneWittenRelatedness")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public String computeRelatedness(
	    @Context HttpServletRequest req,
	    @FormParam("source") List<String> sources,
			@FormParam("target") List<String> targets) {
	  long start = System.currentTimeMillis();
	  
		Set<KBIdentifiedEntity> sourceIds = new HashSet<>(sources.size());
		for (String s : sources) {
			sourceIds.add(new KBIdentifiedEntity(s));
		}
		Set<KBIdentifiedEntity> targetIds = new HashSet<>(targets.size());
		for (String t : targets) {
			targetIds.add(new KBIdentifiedEntity(t));
		}
		Entities sourceEntities = AidaManager.getEntities(sourceIds);
		Entities targetEntities = AidaManager.getEntities(targetIds);
		Entities allEntities = new Entities();
		allEntities.addAll(sourceEntities);
		allEntities.addAll(targetEntities);

		EntityEntitySimilarity eeSim = null;
		try {
			eeSim = EntityEntitySimilarity.getMilneWittenSimilarity(
					allEntities, new NullTracer());
		} catch (Exception e1) {
			return "Error creating EE-Similarity processor.";
		}

		JSONObject result = new JSONObject();

		for (Entity s : sourceEntities) {
			JSONObject sObject = new JSONObject();
			for (Entity t : targetEntities) {
				try {
					double d = eeSim.calcSimilarity(s, t);
					sObject.put(t.getKbIdentifiedEntity().getDictionaryKey(), d);
				} catch (Exception e1) {
					return "Error computing similarity.";
				}
			}
			result.put(s.getKbIdentifiedEntity().getDictionaryKey(), sObject);
		}

		long dur = System.currentTimeMillis() - start;
		
    StringBuilder sb = new StringBuilder();
    sb.append("RELATEDNESS ");
    sb.append(sources.size()).append(" ");
    sb.append(targets.size()).append(" ");
    sb.append(dur).append("ms");
    logger.info(sb.toString());
		
		return result.toJSONString();
	}

	private void adjustSimSettingsForNewPriorWeight(
			SimilaritySettings simSettings, double newPriorWeight) {
		double oldPriorWeight = simSettings.getPriorWeight();
		if (oldPriorWeight == 1)
			return;
		List<String[]> mentionEntitySims = simSettings
				.getMentionEntitySimilarities();
		for (String[] sim : mentionEntitySims) {
			double oldWeight = Double.parseDouble(sim[2]);
			double newWeight = oldWeight
					* ((1 - newPriorWeight) / (1 - oldPriorWeight));
			sim[2] = Double.toString(newWeight);
		}
		simSettings.setMentionEntitySimilarities(mentionEntitySims);
		simSettings.setPriorWeight(newPriorWeight);
	}

	@SuppressWarnings("unused")
	private void adjustSimSettingsForNewImportanceWeight(
			SimilaritySettings simSettings, double newImportanceWeight) {
		// this works only assuming there is one entity importance source
		List<String[]> importancesSettings = simSettings
				.getEntityImportancesSettings();
		if (importancesSettings == null) {// the measure has no importances
											// settings
			return;
		}
		if (importancesSettings.size() != 1) {
			logger.error("SimilaritySettings has more than one entity imporance source"
					+ ", cannot readjust the weights!");
			throw new IllegalArgumentException(
					"Mutliple Importance, cannot readjust the weights");
		}

		double oldImportanceWeight = Double.parseDouble(importancesSettings
				.get(0)[1]);
		if (oldImportanceWeight == 1)
			return;
		importancesSettings.get(0)[1] = Double.toString(newImportanceWeight);

		List<String[]> mentionEntitySims = simSettings
				.getMentionEntitySimilarities();
		for (String[] sim : mentionEntitySims) {
			double oldWeight = Double.parseDouble(sim[2]);
			double newWeight = oldWeight
					* ((1 - newImportanceWeight) / (1 - oldImportanceWeight));
			sim[2] = Double.toString(newWeight);
		}
		simSettings.setMentionEntitySimilarities(mentionEntitySims);

		double oldPriorWeight = simSettings.getPriorWeight();
		double newPriorWeight = oldPriorWeight
				* ((1 - newImportanceWeight) / (1 - oldImportanceWeight));
		simSettings.setPriorWeight(newPriorWeight);

	}

	// format of the string source1:weight1,source2:weight2, ....
	private List<String[]> buildKeyphrasesSourcesWeights(String allWeightsString) {
		List<String[]> keyphrasesSourcesWeights = new LinkedList<String[]>();
		for (String entry : allWeightsString.split(",")) {
			int lastIndexOfColon = entry.lastIndexOf(":");
			String source = entry.substring(0, lastIndexOfColon);
			String weight = entry.substring(lastIndexOfColon + 1);

			keyphrasesSourcesWeights.add(new String[] { source, weight });
		}

		return keyphrasesSourcesWeights;
	}

	public static synchronized void incrememtProcessCount() {
		++processCount;
	}
}