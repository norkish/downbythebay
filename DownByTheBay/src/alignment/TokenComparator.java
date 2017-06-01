package alignment;

public class TokenComparator {
	public static boolean matchCharactersGenerally(char charA, char charB) {
		if (Character.isWhitespace(charA))
			return Character.isWhitespace(charB);
		
		switch(Character.getType(charA)){
		case Character.LOWERCASE_LETTER:
			return charA == Character.toLowerCase(charB);
		case Character.UPPERCASE_LETTER:
			return charA == Character.toUpperCase(charB);
		case Character.OTHER_PUNCTUATION:
			return Character.getType(charB) == Character.OTHER_PUNCTUATION;
		}
		
		return charA == charB;
	}

}
