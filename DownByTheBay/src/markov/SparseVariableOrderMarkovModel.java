package markov;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public class SparseVariableOrderMarkovModel<T> extends AbstractMarkovModel<T>{

	T[] states;
	Map<LinkedList<Integer>,Map<Integer,Double>> logTransitions;
	Map<T, Integer> stateIndex;
	Random rand = new Random();
	int order;
	
	/*
	 * Makes deep copy of all input params for new instance of Markov Model
	 */
	@SuppressWarnings("unchecked")
	public SparseVariableOrderMarkovModel(T[] states, Map<LinkedList<Integer>,Map<Integer,Double>> transitions) {
		this.states = (T[]) new Object[states.length];
		this.logTransitions = new HashMap<LinkedList<Integer>, Map<Integer, Double>>(transitions.size());
		
		Map<Integer, Double> newInnerMap, oldInnerMap;
		for (int i = 0; i < states.length; i++) {
			assert(!stateIndex.containsKey(states[i]));
			this.states[i] = states[i];
			stateIndex.put(states[i], i);
		}
		
		for (LinkedList<Integer> key : transitions.keySet()) {
			this.order = key.size();
			newInnerMap = new HashMap<Integer, Double>();
			this.logTransitions.put(key, newInnerMap);
			oldInnerMap = transitions.get(key);
			for (Entry<Integer, Double> entry : oldInnerMap.entrySet()) {
				newInnerMap.put(entry.getKey(),Math.log(entry.getValue()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	public SparseVariableOrderMarkovModel(Map<T, Integer> statesByIndex, Map<LinkedList<Integer>, Map<Integer, Double>> transitions) {
		this.states = (T[]) new Object[statesByIndex.size()];
		this.stateIndex = statesByIndex;
		this.logTransitions = new HashMap<LinkedList<Integer>, Map<Integer, Double>>(transitions.size());
		
		Map<Integer, Double> newInnerMap, oldInnerMap;
		int i;
		for (Entry<T,Integer> stateIdx: stateIndex.entrySet()) {
			i = stateIdx.getValue();
			this.states[i] = stateIdx.getKey();
		}
		
		for (LinkedList<Integer> key : transitions.keySet()) {
			this.order = key.size();
			newInnerMap = new HashMap<Integer, Double>();
			this.logTransitions.put(key, newInnerMap);
			oldInnerMap = transitions.get(key);
			if (oldInnerMap != null)
				for (Entry<Integer, Double> entry : oldInnerMap.entrySet()) {
					newInnerMap.put(entry.getKey(),Math.log(entry.getValue()));
				}
		}
	}

	public double probabilityOfSequence(T[] seq) {
		double logProb = 0;
		
		if(seq.length == 0)
			return Double.NaN;
		
		LinkedList<Integer> prefix = new LinkedList<Integer>(Collections.nCopies(order, START_TOKEN));
		
		int nextStateIndex = stateIndex.get(seq[0]);
		Map<Integer, Double> innerMap;
		Double value;
		for (int i = 0; i < seq.length; i++) {
			nextStateIndex = stateIndex.get(seq[i]);

			innerMap = logTransitions.get(prefix);
			if (innerMap == null)
				return 0.;
			value = innerMap.get(nextStateIndex);
			if (value == null)
				return 0.;
			
			logProb += value;
			prefix.add(nextStateIndex);
			prefix.remove(0);
		}
		
		return Math.exp(logProb);
	}

	public String toString()
	{
		StringBuilder str = new StringBuilder();
		
		str.append("logTransitions:");
		for (Entry<LinkedList<Integer>, Map<Integer, Double>> entry: logTransitions.entrySet()) {
			str.append("[");
			for (Entry<Integer, Double> innerEntry : entry.getValue().entrySet()) {
				str.append("\n\t");
				str.append(entry.getKey());
				str.append(" - ");				
				str.append(innerEntry.getKey());				
				str.append(" : ");				
				str.append(Math.exp(innerEntry.getValue()));
			}
			str.append("\n]\n\n");
		}
		
		return str.toString();
		
	}

	@Override
	public List<T> generate(int length) {
		int nextStateIdx = -1;
		
		List<T> newSeq = new ArrayList<T>();
		LinkedList<Integer> prefix = new LinkedList<Integer>(Collections.nCopies(order, START_TOKEN));

		for (int i = 0; i < length; i++) {
			nextStateIdx = sampleNextStateIdx(prefix);
			
			if(nextStateIdx == END_TOKEN)
			{
				return newSeq;
			}

			newSeq.add(states[nextStateIdx]);
			prefix.remove(0);
			prefix.add(nextStateIdx);
		}
		
		return newSeq;
	}
	
	private int sampleNextStateIdx(List<Integer> prevStateIdx) {
		double randomDouble = rand.nextDouble();
		
		double accumulativeProbability = 0.;
		
		Map<Integer, Double> transForPrevState = logTransitions.get(prevStateIdx);
		if (transForPrevState != null) {
			for (Entry<Integer, Double> entry : transForPrevState.entrySet()) {
				accumulativeProbability += Math.exp(entry.getValue());
				if (accumulativeProbability >= randomDouble)
				{
					return entry.getKey();
				}
			}
		}
		
		return -1;
	}

	public T sampleNextState(List<T> tokenPrefix) {
		LinkedList<Integer> prefix = new LinkedList<Integer>();
		for (T prevState : tokenPrefix) {
			prefix.add(stateIndex.get(prevState));
		}
		if (!logTransitions.containsKey(prefix))
			throw new RuntimeException("Model does not contain prefix: " + tokenPrefix);

		int nextStateIdx = sampleNextStateIdx(prefix);
		return states[nextStateIdx];
	}
}
