package markov;
import java.util.List;

public abstract class AbstractMarkovModel<T> {

	public static final int START_TOKEN = -2;
	public static final int END_TOKEN = -1;
	
	abstract public double probabilityOfSequence(T[] seq);
	
	abstract public List<T> generate(int length);
	
}
