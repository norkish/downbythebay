package markov;

public class Token {
	
	private static Token startToken = new Token();
	private static Token endToken = new Token();
	
	public static Token getStartToken() {
		return startToken;
	}
	
	public static Token getEndToken() {
		return endToken;
	}
	
	public String toString() {
		if (this == startToken) {
			return "<S>";
		} else if (this == endToken) {
			return "<END>";
		} else {
			return "UNK";
		}
	}
}
