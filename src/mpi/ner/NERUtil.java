package mpi.ner;

import java.util.ArrayList;
import java.util.List;

public class NERUtil {

	public static List<Name> findNamesFromPOS(List<PosToken> tokens) {
		boolean previousWasAName = false;
		List<Name> names = new ArrayList<Name>();
		for (int i = 0; i < tokens.size(); i++) {
			// System.out.println(tagging.token(i) + "\t" + tagging.tag(i) +
			// " ");

			String posTag = tokens.get(i).getPosTag();
			if (posTag.equals("np") || posTag.equals("NNP") ) {

				Name n = new Name(tokens.get(i));
				if (previousWasAName) {
					Name previous = names.get(names.size() - 1);
					previous.setName(previous.getName() + " " + n.getName());
					//
					previous.setLength(previous.getLength() + n.getLength() + 1);
				} else {
					names.add(n);
				}
				previousWasAName = true;
			} else {
				previousWasAName = false;
			}
		}
		return names;
	}

}
