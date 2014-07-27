package mpi.ner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import mpi.aida.data.Mention;
import mpi.aida.data.Mentions;
import mpi.aida.util.ClassPathUtils;
import mpi.aida.util.timing.RunningTimer;
import mpi.tokenizer.data.Token;
import mpi.tokenizer.data.Tokens;
import mpi.tools.javatools.datatypes.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.Collections;
import gnu.trove.map.hash.TIntObjectHashMap;

public class NERManager {

	private static final Logger logger_ = LoggerFactory
			.getLogger(NERManager.class);

	private final List<NER> ners;
	private final List<MentionNormalizer> normalizers;

	private static class NERManagerHolder {

		public static NERManager nerManager = new NERManager();
	}

	public static NERManager singleton() {
		return NERManagerHolder.nerManager;
	}

	private NERManager() {
		ners = new LinkedList<>();
		normalizers = new LinkedList<>();
		
		String nerSettingsFile = "ner.properties";
		Properties prop = null;
		try {
			prop = ClassPathUtils.getPropertiesFromClasspath(nerSettingsFile);
		} catch (IOException e) {
			logger_.error("Couldn't load NER Configuration from: "
					+ nerSettingsFile);
			e.printStackTrace();
		}
		String taggers = prop.getProperty("taggers");
		String[] taggersList = taggers.split(",");
		for (String tagger : taggersList) {
			ners.add(NERFactory.createNER(NERTaggerName.valueOf(tagger)));
		}
		
		String normalizersString = prop.getProperty("normalizers");
		String[] normalizersList = normalizersString.split(","); 
		for(String normalizer : normalizersList) {
		  normalizers.add(NERFactory.createMentionNormalizer(MentionNormalizerName.valueOf(normalizer)));
		}
	}

	public Mentions findMentions(String docId, String text, Tokens tokens) {
		List<Name> names = findNames(docId, text, tokens);
		return alignNameAndCreateMentions(names, tokens);
	}

	// Tokens are passed to make sure the returned names are aligned to the
	// tokens
	private List<Name> findNames(String docId, String text, Tokens tokens) {

		Map<String, List<Name>> annotations = new HashMap<String, List<Name>>();
		// run the NERs in paralle to fill in the map

		for (NER ner : ners) {
			String id = ner.getId();
			List<Name> names = ner.findNames(docId, text);
			logger_.debug(id);
			for (Name name : names) {
			  logger_.debug(name.toString());
			}
			annotations.put(id, names);
		}
		List<Name> allAnnotations = reconcileAnnotations(annotations, tokens);
		// align annotations to the original tokens

		// System.out.println("Recondiled Names");
		// for(Name name: allAnnotations) {
		// System.out.println(name);
		// }
		//

		return allAnnotations;
	}

	protected List<Name> reconcileAnnotations(
			Map<String, List<Name>> annotations, Tokens tokens) {
		// TODO: make sure the mentions are aligned with the given tokens
		List<Name> allNames = new ArrayList<Name>();
		// we need to spot the overlaps, so first put all the annotation in the
		// same list
		for (List<Name> names : annotations.values()) {
			allNames.addAll(names);
		}
		// sort by start position
		Collections.sort(allNames, new Name.SortByStartPosition());
		Name previous = null;
		List<Name> reconciled = new LinkedList<Name>();
		List<Name> overlapping = new ArrayList<Name>();
		int maxEnd = -1;

		for (Name n : allNames) {
			if (previous == null) {
				previous = n;
				maxEnd = n.getEnd();
				overlapping.add(n);
				continue;
			}
			if (n.getStart() <= maxEnd) {
				overlapping.add(n);

			} else {
				reconciled.add(reconcileOverlappingNames(overlapping));

				overlapping.clear();
				overlapping.add(n);
			}
			maxEnd = Math.max(maxEnd, n.getEnd());

		}
		if (!overlapping.isEmpty()) {
			reconciled.add(reconcileOverlappingNames(overlapping));
		}
		return reconciled;
	}

	private Name reconcileOverlappingNames(List<Name> overlapping) {
		assert overlapping.size() > 0;
		if (overlapping.size() == 1)
			return overlapping.get(0);
		// return the longest;
		Name longest = overlapping.get(0);
		Set<String> annotators = new HashSet<String>();
		annotators.add(longest.getNerAnnotatorId());
		for (int i = 1; i < overlapping.size(); i++) {
			annotators.add(overlapping.get(i).getNerAnnotatorId());
			if (overlapping.get(i).getLength() > longest.getLength()) {
				longest = overlapping.get(i);
			}
		}
		// set score to the number of taggers that agree on the name entity
		longest.setScore(annotators.size());

		return longest;
	}

	private Mentions alignNameAndCreateMentions(List<Name> names, Tokens tokens) {
		Integer id = RunningTimer.recordStartTime("NERManager:alignName");
	  Mentions mentions = new Mentions();

		TIntObjectHashMap<Pair<Token, Integer>> characterOffsetToTokenMap = new TIntObjectHashMap<>();
		for(int i=0; i<tokens.size(); i++) {
		  Token token = tokens.getToken(i);
		  for(int j = token.getBeginIndex(); j <= token.getEndIndex(); j++) {
		    characterOffsetToTokenMap.put(j, new Pair<Token, Integer>(token, i));
		  }
		}
		
		
		
		for (Name name : names) {
			Pair<Token, Integer> startToken = characterOffsetToTokenMap.get(name.getStart());
			Pair<Token, Integer> endToken = characterOffsetToTokenMap.get(name.getEnd());
		
      int startTokenOffset = startToken.first.getBeginIndex();
      int endTokenOffset = endToken.first.getEndIndex();
      int startTokenIndex = startToken.second;
      int endTokenIndex = endToken.second;

			Mention mention = new Mention();
			mention.setCharOffset(startTokenOffset);
			mention.setCharLength(endTokenOffset - startTokenOffset);
			mention.setMention(tokens.toText(startTokenIndex, endTokenIndex));
			mention.setStartToken(startTokenIndex);
			mention.setEndToken(endTokenIndex);
			mention.setStartStanford(startToken.first.getStandfordId());
			mention.setEndStanford(endToken.first.getStandfordId());
			for(MentionNormalizer normalizer: normalizers) {
			  String normalizedMention = normalizer.normalize(mention.getMention());
			  mention.getNormalizedMention().add(normalizedMention);
			}

			mentions.addMention(mention);
		}
		RunningTimer.recordEndTime("NERManager:alignName", id);
		return mentions;
	}
	
	
//	public static void main(String[] args) {
//    NERManager nerManager = NERManager.singleton();
//    String text = "Albert Einstein was born in Ulm.";
//    Tokens tokens = TokenizerManager.tokenize(text, type.TOKENS, false);
//    System.out.println(nerManager.findMentions("test", text , tokens));
//  }
}
