package linguistic.phonetic.syllabic;

import linguistic.phonetic.*;

import java.util.List;
import java.io.Serializable;
import java.util.ArrayList;

public final class Syllable implements Serializable {

	private ConsonantPronunciation onset = new ConsonantPronunciation();
	private VowelPhoneme nucleus;
	private ConsonantPronunciation coda = new ConsonantPronunciation();

	public Syllable() {}

	public Syllable(List<ConsonantPhoneme> onset, VowelPhoneme nucleus, List<ConsonantPhoneme> coda) {
		this.setOnset(onset);
		this.setNucleus(nucleus);
		this.setCoda(coda);
	}

	public List<Phoneme> getPronunication() {
		List<Phoneme> result = new ArrayList<Phoneme>();
		if (this.hasOnset())
			result.addAll(this.getOnset());
		if (this.hasNucleus())
			result.add(this.getNucleus());
		if (this.hasCoda())
			result.addAll(this.getCoda());
		return result;
	}

	public VowelPhoneme getVowel() {
		return this.getNucleus();
	}

	public ConsonantPronunciation getConsonants() {
		ConsonantPronunciation consonants = new ConsonantPronunciation();
		consonants.addAll(this.getOnset());
		consonants.addAll(this.getCoda());
		return consonants;
	}

	public Pronunciation getRhyme() {
		Pronunciation rhyme = new Pronunciation();
		if (nucleus != null && nucleus.phonemeEnum != null)
			rhyme.add(nucleus);
		if (coda != null && !coda.isEmpty())
			rhyme.addAll(coda);
		return rhyme;
	}

	public Pronunciation getAllPhonemes() {
		Pronunciation all = new Pronunciation();
		if (onset != null && !onset.isEmpty())
			all.addAll(onset);
		if (nucleus != null && nucleus.phonemeEnum != null)
			all.add(nucleus);
		if (coda != null && !coda.isEmpty())
			all.addAll(coda);
		return all;
	}

	public ConsonantPronunciation getOnset() {
		return onset;
	}

	public void setOnset(List<ConsonantPhoneme> onset) {
		this.onset = (ConsonantPronunciation) onset;
	}

	public ConsonantPronunciation getCoda() {
		return coda;
	}

	public void setCoda(List<ConsonantPhoneme> coda) {
		this.coda = (ConsonantPronunciation) coda;
	}

	public void setRhyme(VowelPhoneme nucleus, ConsonantPronunciation coda) {
		this.setNucleus(nucleus);
		this.setCoda(coda);
	}

	public VowelPhoneme getNucleus() {
		return nucleus;
	}

	public void setNucleus(VowelPhoneme nucleus) {
		this.nucleus = nucleus;
	}

	public int getStress() {
		return nucleus.stress;
	}

	public void setStress(int stress) {
		nucleus.stress = stress;
	}

	public boolean hasOnset() {
		if (this.onset != null && !this.onset.isEmpty())
			return true;
		return false;
	}

	public boolean hasNucleus() {
		if (this.nucleus != null)
			return true;
		return false;
	}

	public boolean hasCoda() {
		if (this.coda != null && !this.coda.isEmpty())
			return true;
		return false;
	}

	@Override
	public String toString() {
		return "" +  onset.toString() + nucleus.toString() + coda.toString();
	}

	public String toString(Object instanceOfDesiredResultFormat) {
		//TODO implement this
		return "" + onset.toString() + nucleus.toString() + coda.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Syllable syllable = (Syllable) o;

		if (getOnset() == null && syllable.getOnset() != null) return false;
		if (getOnset() != null && syllable.getOnset() == null) return false;
		if (getNucleus() == null && syllable.getNucleus() != null) return false;
		if (getNucleus() != null && syllable.getNucleus() == null) return false;
		if (getCoda() == null && syllable.getCoda() != null) return false;
		if (getCoda() != null && syllable.getCoda() == null) return false;

		if (getOnset() != null ? !getOnset().equals(syllable.getOnset()) : syllable.getOnset() != null) return false;
		if (getNucleus() != null ? !getNucleus().equals(syllable.getNucleus()) : syllable.getNucleus() != null)
			return false;
		return getCoda() != null ? getCoda().equals(syllable.getCoda()) : syllable.getCoda() == null;
	}

	@Override
	public int hashCode() {
		int result = getOnset() != null ? getOnset().hashCode() : 0;
		result = 31 * result + (getNucleus() != null ? getNucleus().hashCode() : 0);
		result = 31 * result + (getCoda() != null ? getCoda().hashCode() : 0);
		return result;
	}

	public boolean equalsSansStress(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Syllable syllable = (Syllable) o;

		if (getOnset() != null ? !getOnset().equals(syllable.getOnset()) : syllable.getOnset() != null) return false;
		if (getNucleus() != null ? !getNucleus().equalsSansStress(syllable.getNucleus()) : syllable.getNucleus() != null)
			return false;
		return getCoda() != null ? getCoda().equals(syllable.getCoda()) : syllable.getCoda() == null;
	}

}
