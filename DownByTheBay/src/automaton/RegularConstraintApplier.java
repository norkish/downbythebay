package automaton;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.time.StopWatch;

import dbtb.constraint.ConditionedConstraint;
import dbtb.constraint.StatesConstraint;
import dbtb.data.DataLoader;
import dbtb.data.SyllableToken;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderMarkovModel.CharacterToken;
import dbtb.markov.SparseVariableOrderNHMMMultiThreaded;
import dbtb.markov.Token;
import dbtb.markov.UnsatisfiableConstraintSetException;
import dbtb.utils.Pair;
import dbtb.utils.Utils;

public class RegularConstraintApplier {

	public static class StateToken<T1 extends Token> extends Token {
		public T1 token;
		public Integer state;
		public StateToken(T1 token, Integer state) {
			this.token = token;
			this.state = state;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((state == null) ? 0 : state.hashCode());
			result = prime * result + ((token == null) ? 0 : token.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			StateToken other = (StateToken) obj;
			if (state == null) {
				if (other.state != null)
					return false;
			} else if (!state.equals(other.state))
				return false;
			if (token == null) {
				if (other.token != null)
					return false;
			} else if (!token.equals(other.token))
				return false;
			return true;
		}
		@Override
		public String toString() {
			if (token instanceof SyllableToken)
				return ((SyllableToken) token).getStringRepresentationIfFirstSyllable() + ",q" + state;
			else 
				return token + ",q" + state;
		}
		
		
	}

	public static DecimalFormat df2 = new DecimalFormat("#.##");

	public static <T extends Token> FactorGraph<T> combineAutomataWithMarkovInFactorGraph(SparseVariableOrderMarkovModel<T> M, 
			Automaton<T> A, int length, List<List<ConditionedConstraint<StateToken<T>>>> constraints) {
		
		final BidirectionalVariableOrderPrefixIDMap<T> oldPrefixMap = M.stateIndex;
		assert (A.sigma == oldPrefixMap);
		final Map<Integer, Map<Integer, Double>> oldTransitions = M.logTransitions;
		final HashMap<Integer, Double> oldLogPriors = M.logPriors;
		final Map<Integer, Map<Integer, Integer>> delta = A.delta;
		
		BidirectionalVariableOrderPrefixIDMap<StateToken<T>> newPrefixMap = new BidirectionalVariableOrderPrefixIDMap<StateToken<T>>(oldPrefixMap.getOrder()); 
		Map<Integer,Map<Integer,Double>> f = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer,Map<Integer,Double>> g = new HashMap<Integer, Map<Integer, Double>>();

		for (int i = 1; i <= length; i++) {
			final HashMap<Integer, Double> g_i = new HashMap<Integer, Double>();
			g.put(i, g_i);
		}
		
		Integer fromStateTokenIdx, toStateTokenIdx;
		Double probability;
	
		Map<Integer, Double> g1 = g.get(1);
		Map<Integer, Integer> deltaInnerMap = delta.get(0); 
		for (Integer aIdx : deltaInnerMap.keySet()) { // for all transitions in ∂ that have label a
			if (oldLogPriors.containsKey(aIdx)) {
				LinkedList<T> a = oldPrefixMap.getPrefixForID(aIdx);
				Integer qTo = deltaInnerMap.get(aIdx);
				toStateTokenIdx = newPrefixMap.addPrefix(createStateTokenPrefix(a,qTo));
				g1.put(toStateTokenIdx, Math.exp(oldLogPriors.get(aIdx))); // then the new priors allow a with it's previous prior (to be normalized)
			}
		}
		
		// for every q in delta
		for (Integer qIdx : delta.keySet()) {
			deltaInnerMap = delta.get(qIdx);
			// for every (q,a') -> ? in delta
			for (Integer aPrimeIdx : deltaInnerMap.keySet()) {
				LinkedList<T> aPrime = oldPrefixMap.getPrefixForID(aPrimeIdx);
				// for every a->? in M's transition matrix
				for(Integer aIdx: oldTransitions.keySet()) {
					Map<Integer, Double> transitionsInnerMap = oldTransitions.get(aIdx);
					// if a->a' is a valid transition
					probability = transitionsInnerMap.get(aPrimeIdx);
					if (probability != null) {
						probability = Math.exp(probability);
						LinkedList<T> a = oldPrefixMap.getPrefixForID(aIdx);
						fromStateTokenIdx = newPrefixMap.addPrefix(createStateTokenPrefix(a,qIdx)); // get the id for the from token
						toStateTokenIdx = newPrefixMap.addPrefix(createStateTokenPrefix(aPrime,deltaInnerMap.get(aPrimeIdx))); // and the id for the to token
						Utils.setValueForKeys(f, fromStateTokenIdx, toStateTokenIdx, probability); // and add it to f
					}
				}
			}
		}
//		Utils.normalizeByFirstDimension(f);
		
		for (int i = 2; i <= length; i++) {
			final Map<Integer, Double> map = g.get(i);
			for (int j = 0; j < newPrefixMap.getPrefixCount(); j++) {
				if (i < length || A.acceptingStates.contains(newPrefixMap.getPrefixFinaleForID(j).state)) {
					map.put(j, 1.0/oldPrefixMap.getPrefixCount());
				}
			}
		}
//		Utils.normalizeByFirstDimension(g);
		
		return new FactorGraph<T>(f,g,newPrefixMap,length);
	}
	
	public static <T extends Token> SparseVariableOrderNHMMMultiThreaded<StateToken<T>> combineAutomataWithMarkov(SparseVariableOrderMarkovModel<T> M, 
			Automaton<T> A, int length, List<List<ConditionedConstraint<StateToken<T>>>> constraints) throws UnsatisfiableConstraintSetException, InterruptedException {
		
		final BidirectionalVariableOrderPrefixIDMap<T> oldPrefixMap = M.stateIndex;
		assert (A.sigma == oldPrefixMap);
		final Map<Integer, Map<Integer, Double>> oldTransitions = M.logTransitions;
		final HashMap<Integer, Double> oldLogPriors = M.logPriors;
		final Map<Integer, Map<Integer, Integer>> delta = A.delta;
		
		BidirectionalVariableOrderPrefixIDMap<StateToken<T>> newPrefixMap = new BidirectionalVariableOrderPrefixIDMap<StateToken<T>>(oldPrefixMap.getOrder()); 
		Map<Integer,Double> newPriors = new HashMap<Integer, Double>();
		Map<Integer,Map<Integer, Double>> newTransitions = new HashMap<Integer,Map<Integer,Double>>();

		
		// A.delta : first index is for label a (in sigma), second index is for state q,  
		// and resulting value is the index for state q' s.t. ∂(q,a) = q'
		
		Integer fromStateTokenIdx, toStateTokenIdx;
		Double probability;
		
		Map<Integer, Integer> deltaInnerMap = delta.get(0); 
		for (Integer aIdx : deltaInnerMap.keySet()) { // for all transitions in ∂ that have label a
			if (oldLogPriors.containsKey(aIdx)) {
				LinkedList<T> a = oldPrefixMap.getPrefixForID(aIdx);
				Integer qTo = deltaInnerMap.get(aIdx);
				toStateTokenIdx = newPrefixMap.addPrefix(createStateTokenPrefix(a,qTo));
				newPriors.put(toStateTokenIdx, Math.exp(oldLogPriors.get(aIdx))); // then the new priors allow a with it's previous prior (to be normalized)
			}
		}
//		Utils.normalize(newPriors);
		
		// for every q in delta
		for (Integer qIdx : delta.keySet()) {
			deltaInnerMap = delta.get(qIdx);
			// for every (q,a') -> ? in delta
			for (Integer aPrimeIdx : deltaInnerMap.keySet()) {
				LinkedList<T> aPrime = oldPrefixMap.getPrefixForID(aPrimeIdx);
				// for every a->? in M's transition matrix
				for(Integer aIdx: oldTransitions.keySet()) {
					Map<Integer, Double> transitionsInnerMap = oldTransitions.get(aIdx);
					// if a->a' is a valid transition
					probability = transitionsInnerMap.get(aPrimeIdx);
					if (probability != null) {
						probability = Math.exp(probability);
						LinkedList<T> a = oldPrefixMap.getPrefixForID(aIdx);
						fromStateTokenIdx = newPrefixMap.addPrefix(createStateTokenPrefix(a,qIdx)); // get the id for the from token
						toStateTokenIdx = newPrefixMap.addPrefix(createStateTokenPrefix(aPrime,deltaInnerMap.get(aPrimeIdx))); // and the id for the to token
						Utils.setValueForKeys(newTransitions, fromStateTokenIdx, toStateTokenIdx, probability);
					}
				}
			}
		}
//		Utils.normalizeByFirstDimension(newTransitions);

//		System.out.println("\nNew Priors");
//		
//		for (Integer priorKey : newPriors.keySet()) {
//			System.out.println(newPrefixMap.getPrefixFinaleForID(priorKey) + " : " + newPriors.get(priorKey));
//		}
//		
//		System.out.println("\nNew Transitions");
//		int prefixCount = newPrefixMap.getPrefixCount();
//		for (int i = 0; i < prefixCount; i++) {
//			Map<Integer, Double> fromMap = newTransitions.get(i);
//			if (fromMap == null) continue;
//			for (int j = 0; j < prefixCount; j++) {
//				Double double1 = fromMap.get(j);
//				if (double1 != null)
//					System.out.println("(" + newPrefixMap.getPrefixFinaleForID(i) + ") -> (" + newPrefixMap.getPrefixFinaleForID(j) + ") : " + df2.format(double1));
//			}
//		}
//		System.out.println();
		
		
		SparseVariableOrderMarkovModel<StateToken<T>> combinedMarkovModel = new SparseVariableOrderMarkovModel<StateToken<T>>(newPrefixMap,newPriors,newTransitions);
//		System.out.println(combinedMarkovModel.toString());
		List<List<ConditionedConstraint<StateToken<T>>>> newConstraints = modifyConstraints(constraints);
		
		// add constraints to start in state reachable from q0 and end in one of accepting states, and generally apply g_i
		
//		newConstraints.get(0).add(new ConditionedConstraint<StateToken<T>>(new StatesConstraint<StateToken<T>>(acceptableStartStates)));
		newConstraints.get(length-1).add(new ConditionedConstraint<StateToken<T>>(new StatesConstraint<StateToken<T>>(A.acceptingStates)));
		
		return new SparseVariableOrderNHMMMultiThreaded<StateToken<T>>(combinedMarkovModel, length, newConstraints);
	}
	
	private static <T extends Token> LinkedList<StateToken<T>> createStateTokenPrefix(LinkedList<T> token, Integer state) {
		LinkedList<StateToken<T>> stateTokenPrefix = new LinkedList<StateToken<T>>();
		
		for (T tokenElement : token) {
			stateTokenPrefix.add(new StateToken<T>(tokenElement, state));
		}
		
		return stateTokenPrefix;
	}

	private static <T extends Token> List<List<ConditionedConstraint<StateToken<T>>>> modifyConstraints(
			List<List<ConditionedConstraint<StateToken<T>>>> constraints) {
		List<List<ConditionedConstraint<StateToken<T>>>> newConstraints = new ArrayList<List<ConditionedConstraint<StateToken<T>>>>();
		
		for (int i = 0; i < constraints.size(); i++) {
			List<ConditionedConstraint<StateToken<T>>> newConstraintsForPos = new ArrayList<ConditionedConstraint<StateToken<T>>>();
			newConstraints.add(newConstraintsForPos);
			List<ConditionedConstraint<StateToken<T>>> constraintsForPos = constraints.get(i);
			for (ConditionedConstraint<StateToken<T>> conditionedConstraint : constraintsForPos) {
				newConstraintsForPos.add(conditionedConstraint);
			}
		}
	
		return newConstraints;
	}

	public static void main(String[] args) throws UnsatisfiableConstraintSetException, InterruptedException {
//		runExample1(); // 4-length 1-order NHMM with regular constraint: {aa+b+}
//		runExample2(); // dead bear with Ed with bed hair
//		runExample3(); // 3-length 1-order NHMM with regular constraint: {a+b+}
//		runExample4(); // 3-length 1-order NHMM with regular constraint: {a*ba*}
//		runExample5(); // 4-length 1-order NHMM with regular constraint: {a+ba+}
//		runComparisonTests(); // abracadabra with max order
		slidesExample();
	}

	private static void runExample1() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 1.0);
//		bTransitions.put(bTokenIdx, 0.0);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		// Automaton
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>(); 
		Set<Integer> acceptingStates = new HashSet<Integer>();

		Utils.setValueForKeys(delta, 0, aTokenIdx, 1);
		Utils.setValueForKeys(delta, 1, aTokenIdx, 2);
		Utils.setValueForKeys(delta, 2, aTokenIdx, 2);
		Utils.setValueForKeys(delta, 2, bTokenIdx, 3);
		Utils.setValueForKeys(delta, 3, bTokenIdx, 3);
		
		acceptingStates.add(3);
		
		Automaton<CharacterToken> A = new Automaton<CharacterToken>(prefixMap,delta,acceptingStates);
		
		final int length = 4;
		final ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<CharacterToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<CharacterToken>> NHMM = combineAutomataWithMarkov(M, A, length, constraints);
		
		System.out.println("4-length 1-order NHMM with regular constraint: {aa+b+}");
		Map<String,Double> counts = new HashMap<String, Double>();
		Map<String,Double> probs = new HashMap<String, Double>();
		int SAMPLE_COUNT = 10000;
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final List<StateToken<CharacterToken>> generate = NHMM.generate(length);
			Utils.incrementValueForKey(counts, generate.toString());
			probs.put(generate.toString(), NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
		
		FactorGraph<CharacterToken> factorGraph = combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("4-length 1-order NHMM with regular constraint: {aa+b+}");
		counts = new HashMap<String, Double>();
		probs = new HashMap<String, Double>();

		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final Pair<List<StateToken<CharacterToken>>,Double> generate = factorGraph.generate(length);
			Utils.incrementValueForKey(counts, generate.getFirst().toString());
			probs.put(generate.getFirst().toString(), generate.getSecond());
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
	}

	private static void runExample2() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String trainingSentence = "dead bear with Ed with bed hair";
		DataLoader dl = new DataLoader(markovOrder);
		
		List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
		LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
		Integer toTokenID;
		Integer fromTokenID = prefixMap.addPrefix(prefix);
		for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
			prefix.removeFirst();
			prefix.addLast(trainingSentenceTokens.get(j));
			
			toTokenID = prefixMap.addPrefix(prefix);
			Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
			Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 

			fromTokenID = toTokenID;
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		System.out.println("M:\n" + M.toString());
		
		
		int deadTokenIdx = 0;
		int bearTokenIdx = 1;
		int withTokenIdx = 2;
		int edTokenIdx = 3;
		int bedTokenIdx = 4;
		int hairTokenIdx = 5;
		
		final int length = 4;
		// Automaton
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>(); 
		Set<Integer> acceptingStates = new HashSet<Integer>();
		
		Utils.setValueForKeys(delta, 0, deadTokenIdx, 1);
		Utils.setValueForKeys(delta, 1, bearTokenIdx, 2);
		Utils.setValueForKeys(delta, 2, withTokenIdx, 3);
		Utils.setValueForKeys(delta, 3, bedTokenIdx, 4);
		if (length == 5) {
			Utils.setValueForKeys(delta, 4, hairTokenIdx, 5);
			acceptingStates.add(5);
		} else if (length == 4) {
		// eliminated by c2 constraint
			Utils.setValueForKeys(delta, 3, edTokenIdx, 6);
			Utils.setValueForKeys(delta, 0, bearTokenIdx, 8);
			Utils.setValueForKeys(delta, 8, withTokenIdx, 9);
			Utils.setValueForKeys(delta, 9, bedTokenIdx, 10);
			Utils.setValueForKeys(delta, 10, hairTokenIdx, 11);
		// eliminated by c2 constraint
			acceptingStates.add(4);
			acceptingStates.add(6);
			acceptingStates.add(11);
		}
		Automaton<SyllableToken> A = new Automaton<SyllableToken>(prefixMap,delta,acceptingStates);
		
		System.out.println("A:\n" + A.toString());
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		Map<String,Double> counts = new HashMap<String, Double>();
		Map<String,Double> probs = new HashMap<String, Double>();
		int SAMPLE_COUNT = 10000;
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			Utils.incrementValueForKey(counts, generate.toString());
			probs.put(generate.toString(), NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
		
		FactorGraph<SyllableToken> factorGraph = combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		counts = new HashMap<String, Double>();
		probs = new HashMap<String, Double>();

		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			Utils.incrementValueForKey(counts, generate.getFirst().toString());
			probs.put(generate.getFirst().toString(), generate.getSecond());
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
	}

	private static void runExample3() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .4);
		priors.put(bTokenIdx, .6);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .34);
		aTransitions.put(bTokenIdx, .66);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, .18);
		bTransitions.put(bTokenIdx, .82);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		// Automaton
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>(); 
		Set<Integer> acceptingStates = new HashSet<Integer>();

		Utils.setValueForKeys(delta, 0, aTokenIdx, 1);
		Utils.setValueForKeys(delta, 1, aTokenIdx, 1);
		Utils.setValueForKeys(delta, 1, bTokenIdx, 2);
		Utils.setValueForKeys(delta, 2, bTokenIdx, 2);
		
		acceptingStates.add(2);
		
		Automaton<CharacterToken> A = new Automaton<CharacterToken>(prefixMap,delta,acceptingStates);
		
		final int length = 3;
		final ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<CharacterToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<CharacterToken>> NHMM = combineAutomataWithMarkov(M, A, length, constraints);
		
		System.out.println();
		
		System.out.println("Original probabilities:\n\tabb = .66 * .82 = 0.5412 (0.5412/0.7656 = 0.7068965517)\n\taab = .34 * .66 = 0.2244 (0.2244/0.7656 = 0.2931034483)\n");
		
		System.out.println("3-length 1-order NHMM with regular constraint: {a+b+}");
		Map<String,Double> counts = new HashMap<String, Double>();
		Map<String,Double> probs = new HashMap<String, Double>();
		int SAMPLE_COUNT = 10000;
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final List<StateToken<CharacterToken>> generate = NHMM.generate(length);
			Utils.incrementValueForKey(counts, generate.toString());
			probs.put(generate.toString(), NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
		
		FactorGraph<CharacterToken> factorGraph = combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("3-length 1-order factor graph with regular constraint: {a+b+}");
		counts = new HashMap<String, Double>();
		probs = new HashMap<String, Double>();

		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final Pair<List<StateToken<CharacterToken>>,Double> generate = factorGraph.generate(length);
			Utils.incrementValueForKey(counts, generate.getFirst().toString());
			probs.put(generate.getFirst().toString(), generate.getSecond());
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
	}
	
	private static void runExample4() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .4);
		priors.put(bTokenIdx, .6);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .33);
		aTransitions.put(bTokenIdx, .66);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, .18);
		bTransitions.put(bTokenIdx, .82);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		// Automaton
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>(); 
		Set<Integer> acceptingStates = new HashSet<Integer>();

		Utils.setValueForKeys(delta, 0, aTokenIdx, 0);
		Utils.setValueForKeys(delta, 1, aTokenIdx, 1);
		Utils.setValueForKeys(delta, 0, bTokenIdx, 1);
		
		acceptingStates.add(1);
		
		Automaton<CharacterToken> A = new Automaton<CharacterToken>(prefixMap,delta,acceptingStates);
		
		final int length = 3;
		final ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<CharacterToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<CharacterToken>> NHMM = combineAutomataWithMarkov(M, A, length, constraints);
		
		System.out.println();
		
		System.out.println("3-length 1-order NHMM with regular constraint: {a*ba*}");
		Map<String,Double> counts = new HashMap<String, Double>();
		Map<String,Double> probs = new HashMap<String, Double>();
		int SAMPLE_COUNT = 10000;
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final List<StateToken<CharacterToken>> generate = NHMM.generate(length);
			Utils.incrementValueForKey(counts, generate.toString());
			probs.put(generate.toString(), NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
		
		FactorGraph<CharacterToken> factorGraph = combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		counts = new HashMap<String, Double>();
		probs = new HashMap<String, Double>();

		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final Pair<List<StateToken<CharacterToken>>,Double> generate = factorGraph.generate(length);
			Utils.incrementValueForKey(counts, generate.getFirst().toString());
			probs.put(generate.getFirst().toString(), generate.getSecond());
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
		
	}

	private static void runExample5() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .4);
		priors.put(bTokenIdx, .6);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .33);
		aTransitions.put(bTokenIdx, .66);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, .18);
		bTransitions.put(bTokenIdx, .82);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		// Automaton
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>(); 
		Set<Integer> acceptingStates = new HashSet<Integer>();

		Utils.setValueForKeys(delta, 0, aTokenIdx, 1);
		Utils.setValueForKeys(delta, 1, aTokenIdx, 1);
		Utils.setValueForKeys(delta, 2, aTokenIdx, 3);
		Utils.setValueForKeys(delta, 3, aTokenIdx, 3);
		Utils.setValueForKeys(delta, 1, bTokenIdx, 2);
		
		acceptingStates.add(3);
		
		Automaton<CharacterToken> A = new Automaton<CharacterToken>(prefixMap,delta,acceptingStates);
		
		final int length = 4;
		final ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<CharacterToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<CharacterToken>> NHMM = combineAutomataWithMarkov(M, A, length, constraints);
		
		System.out.println();
		
		System.out.println("4-length 1-order NHMM with regular constraint: {a+ba+}");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<CharacterToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<CharacterToken> factorGraph = combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<CharacterToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
	
	private static void runComparisonTests() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 1.0);
//		bTransitions.put(bTokenIdx, 0.0);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		// Automaton
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>(); 
		Set<Integer> acceptingStates = new HashSet<Integer>();

		Utils.setValueForKeys(delta, 0, aTokenIdx, 1);
		Utils.setValueForKeys(delta, 1, aTokenIdx, 2);
		Utils.setValueForKeys(delta, 2, aTokenIdx, 2);
		Utils.setValueForKeys(delta, 2, bTokenIdx, 3);
		Utils.setValueForKeys(delta, 3, bTokenIdx, 3);
		
		acceptingStates.add(3);
		
		Automaton<CharacterToken> A = new Automaton<CharacterToken>(prefixMap,delta,acceptingStates);
		
		int SAMPLE_COUNT = 100000;
		for (int length = 20; length <= 200; length+=20) {
			final ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<CharacterToken>>>>();
			for (int i = 0; i < length; i++) {
				constraints.add(new ArrayList<ConditionedConstraint<StateToken<CharacterToken>>>());
			}
			Map<Integer, Long> dict = new TreeMap<Integer, Long>();

			StopWatch watch = new StopWatch();
			System.out.println(length+"-length 1-order NHMM with regular constraint: {aa+b+}");
			for (int j = 0; j < 10; j++) {
				watch.start();
				SparseVariableOrderNHMMMultiThreaded<StateToken<CharacterToken>> NHMM = combineAutomataWithMarkov(M, A, length, constraints);
				watch.stop();
				dict.put(0, dict.containsKey(0)? dict.get(0) + watch.getTime() : watch.getTime());
//				int mod = 1;
//				for (int i = 1; i <= SAMPLE_COUNT; i++) {
//					NHMM.generate(length);
////					if (i == mod){ 
////						dict.put(i, dict.containsKey(i)? dict.get(i) + watch.getTime() : watch.getTime());
////						System.out.println(i + "\t" + (dict.get(i)*1.0/1.0));
////						mod *= 10;
////					}
//				}
				dict.put(length, dict.containsKey(length)? dict.get(length) + watch.getTime() : watch.getTime());
				watch.reset();
			}
			System.out.println(length + "\t" + (dict.get(length)*1.0/10));
			
//			for (Integer sampleSize : dict.keySet()) {
//				System.out.println(sampleSize + "\t" + (dict.get(sampleSize)*1.0/1.0));
//			}
			dict = new TreeMap<Integer, Long>();
			System.out.println(length+"-length 1-order Factor Graph with regular constraint: {aa+b+}");
			for (int j = 0; j < 10; j++) {
				watch.start();
				FactorGraph<CharacterToken> factorGraph = combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
				dict.put(0, dict.containsKey(0)? dict.get(0) + watch.getTime() : watch.getTime());
				watch.stop();
//				int mod = 1;
//				for (int i = 1; i <= SAMPLE_COUNT; i++) {
//					factorGraph.generate(length);
////					if (i == mod) {
////						dict.put(i, dict.containsKey(i)? dict.get(i) + watch.getTime() : watch.getTime());
////						System.out.println(i + "\t" + (dict.get(i)*1.0/1.0));
////						mod *= 10;
////					}
//				}
				dict.put(length, dict.containsKey(length)? dict.get(length) + watch.getTime() : watch.getTime());
				watch.reset();
			}
			System.out.println(length + "\t" + (dict.get(length)*1.0/10));

//			for (Integer sampleSize : dict.keySet()) {
//				System.out.println(sampleSize + "\t" + (dict.get(sampleSize)*1.0/1.0));
//			}

		}
	}
	
	private static void slidesExample() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"Clay loves Man","Man loves Clay","Clay loves Man day","Man loves Paul day","Clay loves Paul Fan"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for(String trainingSentence: trainingSentences){
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
	
				fromTokenID = toTokenID;
			}
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		System.out.println("M:\n" + M.toString());
		
		
		int clayTokenIdx = 0;
		int lovesTokenIdx = 1;
		int maryTokenIdx = 2;
		int todayTokenIdx = 3;
		int paulTokenIdx = 4;
		int careyTokenIdx = 5;
		
		final int length = 4;
		// Automaton
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>(); 
		Set<Integer> acceptingStates = new HashSet<Integer>();
		
		Utils.setValueForKeys(delta, 0, maryTokenIdx, 1);
		Utils.setValueForKeys(delta, 1, lovesTokenIdx, 3);
		Utils.setValueForKeys(delta, 3, paulTokenIdx, 8);
		Utils.setValueForKeys(delta, 8, careyTokenIdx, 12);
		// eliminated by c2 constraint
		Utils.setValueForKeys(delta, 0, clayTokenIdx, 2);
		Utils.setValueForKeys(delta, 2, lovesTokenIdx, 5);
		Utils.setValueForKeys(delta, 5, maryTokenIdx, 10);
		Utils.setValueForKeys(delta, 5, paulTokenIdx, 10);
		Utils.setValueForKeys(delta, 10, todayTokenIdx, 12);
		// eliminated by c2 constraint
		acceptingStates.add(12);
		Automaton<SyllableToken> A = new Automaton<SyllableToken>(prefixMap,delta,acceptingStates);
		
		System.out.println("A:\n" + A.toString());
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		Map<String,Double> counts = new HashMap<String, Double>();
		Map<String,Double> probs = new HashMap<String, Double>();
		int SAMPLE_COUNT = 10000;
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			Utils.incrementValueForKey(counts, generate.toString());
			probs.put(generate.toString(), NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
		
		FactorGraph<SyllableToken> factorGraph = combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		counts = new HashMap<String, Double>();
		probs = new HashMap<String, Double>();

		for (int i = 0; i < SAMPLE_COUNT; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			Utils.incrementValueForKey(counts, generate.getFirst().toString());
			probs.put(generate.getFirst().toString(), generate.getSecond());
		}
		
		for (String generate : counts.keySet()) {
			System.out.println("\t\tProb:" + probs.get(generate) + ", XProb:" + (counts.get(generate)/SAMPLE_COUNT) + "\t" + generate);
		}
	}
}
