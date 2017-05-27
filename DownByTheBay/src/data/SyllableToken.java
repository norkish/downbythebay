package data;

import java.util.List;

import linguistic.Phoneme;
import linguistic.Pos;

public class SyllableToken {
	List<Phoneme> phonemes;
	Pos pos;
	int countOfSylsInContext;
	int positionInContext;
	int stress;
	// double uniqueness; future feature
	// rhyme class; future feature
}