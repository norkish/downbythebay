package automaton;

import java.util.Comparator;

import dbtb.data.SyllableToken;
import dbtb.linguistic.phonetic.syllabic.Syllabifier;
import dbtb.linguistic.phonetic.syllabic.Syllable;

public class RhymeComparator implements Comparator<SyllableToken> {
	@Override
	public int compare(SyllableToken syl1Token, SyllableToken syl2Token) {
		Syllable s1 = Syllabifier.tokenToSyllable(syl1Token);
		Syllable s2 = Syllabifier.tokenToSyllable(syl2Token);
//		if ((!s1.getOnset().equals(s2.getOnset()) || !s1.getCoda().equals(s2.getCoda())) && s1.getNucleus().equals(s2.getNucleus()))// && s1.getCoda().equals(s2.getCoda()))
		if (!s1.getOnset().equals(s2.getOnset()) && s1.getNucleus().equals(s2.getNucleus()) && s1.getCoda().equals(s2.getCoda()))
			return 0;
		else
			return -1;
	}

}
