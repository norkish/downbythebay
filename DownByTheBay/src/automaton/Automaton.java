package automaton;

import java.util.Map;
import java.util.Set;

import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.Token;

public class Automaton<T extends Token> {
	// we implicitly define the state q_0 to be state 0
	// other states are also implicitly defined as integers from 1 to n
	
	public BidirectionalVariableOrderPrefixIDMap<T> sigma;
	
	// first index is for state q, second index is label a (in sigma), 
	// and resulting value is the index for state q' s.t. ∂(q,a) = q'
	public Map<Integer,Map<Integer,Integer>> delta;
	
	public Set<Integer> acceptingStates;

	public Automaton(BidirectionalVariableOrderPrefixIDMap<T> sigma, Map<Integer, Map<Integer, Integer>> delta,
			Set<Integer> acceptingStates) {
		super();
		this.sigma = sigma;
		this.delta = delta;
		this.acceptingStates = acceptingStates;
	}

	@Override
	public String toString() {
		return sigma + ", " + delta + ", " + acceptingStates;
	}
	
	
}
