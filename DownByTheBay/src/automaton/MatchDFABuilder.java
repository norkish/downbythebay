package automaton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import automaton.RegularConstraintApplier.StateToken;

import java.util.Set;
import java.util.TreeSet;

import dbtb.constraint.ConditionedConstraint;
import dbtb.data.DataLoader;
import dbtb.data.SyllableToken;
import dbtb.linguistic.phonetic.syllabic.Syllabifier;
import dbtb.linguistic.phonetic.syllabic.Syllable;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderNHMMMultiThreaded;
import dbtb.markov.Token;
import dbtb.markov.UnsatisfiableConstraintSetException;
import dbtb.utils.Pair;
import dbtb.utils.Triple;
import dbtb.utils.Utils;

public class MatchDFABuilder {
	public static <T extends Token> Automaton<T> build(int[] matchConstraintList, SparseVariableOrderMarkovModel<T> markovModel) {
		Map<Integer, Map<Integer, Double>> validMarkovTransitions = markovModel.logTransitions;
		
		int[] equivalenceClassMap = computeEquivalenceClass(markovModel.stateIndex);
		
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>();
		Set<Integer> acceptingStates = new HashSet<Integer>();
		
		int nextQStateID = 0;
		
		// find a state q_i,S by looking up it's position, then the label taken to get to the position, and then set of constraints satisfied by paths from this state
		// this is akin to the "Q" function in Papadoupolos et al, 2014 Avoiding Plagiarism
		Map<Integer, Map<Integer,Map<Map<Integer,Integer>, Integer>>> stateDictionary = new HashMap<Integer, Map<Integer,Map<Map<Integer, Integer>, Integer>>>();
		Utils.setValueForKeys(stateDictionary,0,-1,new HashMap<Integer,Integer>(),nextQStateID++);

		// Lookup using state id yields 1) the sequence position index of the state, 2) all state/labels pairs transitioning to the state and 3) the constraints guaranteed from the state
		// this is akin to the "a" function in Papadoupolos et al, 2014 Avoiding Plagiarism
		Map<Integer, Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>> stateInfo = new HashMap<Integer, Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>>();
		stateInfo.put(0, new Triple<Integer,Map<Integer, Set<Integer>>, Map<Integer, Integer>>(0, new HashMap<Integer,Set<Integer>>(), new HashMap<Integer,Integer>()));
		
		//TODO: optimize to only keep track of previous column's stateDictionary
		
		TreeSet<Integer> statesToRemove = new TreeSet<Integer>();
		
		// for each position i in the sequence
		for (int i = 1; i <= matchConstraintList.length; i++) {
			// this is the position to which it is supposed to match (-1 if matches nothing)
			int matchConstraintAti = matchConstraintList[i-1];
			// these are the states reached from the previous sequence position 
			Map<Integer, Map<Map<Integer, Integer>, Integer>> qStatesInPrevCol = stateDictionary.get(i-1);
			// for each state reached from the previous sequence position
			for (Entry<Integer, Map<Map<Integer, Integer>, Integer>> prevQStateLabeledAndConstraints : qStatesInPrevCol.entrySet()) {
				// get the label taken to get to the state
				final Integer labelOfTransitionToPrevQState = prevQStateLabeledAndConstraints.getKey();
				for (Entry<Map<Integer, Integer>, Integer> prevQStateAndConstraints : prevQStateLabeledAndConstraints.getValue().entrySet()) {
					// get the token that leads to that state (at this stage there should only be one since we haven't combined)
					final Integer prevQState = prevQStateAndConstraints.getValue();
					// get the constraints that are guaranteed from that state 
					Map<Integer, Integer> prevQConstraints = prevQStateAndConstraints.getKey();
					Integer equivalenceClassForThisPosition = prevQConstraints.get(i); 
					final Map<Integer, Double> validToLabels = i == 1? markovModel.logPriors: validMarkovTransitions.get(labelOfTransitionToPrevQState);
					if (validToLabels == null) {
						statesToRemove.add(prevQState);
						continue; 
					}
					for (Integer validToLabel : validToLabels.keySet()) {
						// if validToLabel doesn't satisfy c
						final int equivalenceClassForThisLabel = equivalenceClassMap[validToLabel];
						// If the prevQstate characterized by a constraint c on this position, use c to only add states for labels that satisfy c
						if (equivalenceClassForThisPosition != null && equivalenceClassForThisPosition != equivalenceClassForThisLabel) {
							continue;
						}
						// copy the set of constraints that are guaranteed from the previous state
						Map<Integer, Integer> currentQConstraints = new HashMap<Integer, Integer>(prevQConstraints);
						// remove c since it has now been satisfied at this position
						currentQConstraints.remove(i);
						// if this position constrains another position m_i, add m_i to the constraint that are guaranteed from this state
						if (matchConstraintAti != -1) {
							currentQConstraints.put(matchConstraintAti,equivalenceClassForThisLabel);
						}
						
						// add a state and transition for this validToLabel, given currentQConstraints
						Integer nextQState = Utils.getValueForKeys(stateDictionary, i, validToLabel, currentQConstraints);
						Set<Integer> stateToLabels;
						Map<Integer, Set<Integer>> stateToEdges;
						if (nextQState == null) {
							nextQState = nextQStateID++;
							Utils.setValueForKeys(stateDictionary, i, validToLabel, currentQConstraints, nextQState);
							stateToEdges = new HashMap<Integer,Set<Integer>>();
							stateToLabels = new HashSet<Integer>();
							stateInfo.put(nextQState, new Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>(i, stateToEdges, currentQConstraints));
						} else {
							final Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>> stateToEdgesAndConstraints = stateInfo.get(nextQState);
							stateToEdges = stateToEdgesAndConstraints.getSecond();
							stateToLabels = stateToEdges.get(prevQState);
						}
						stateToLabels.add(validToLabel);
						stateToEdges.put(prevQState, stateToLabels);
						
						Utils.setValueForKeys(delta, prevQState, validToLabel, nextQState);
					}
					if (!delta.containsKey(prevQState)) {
						statesToRemove.add(prevQState);
//						assuming we continue; 
					}
				}
			}
			
			//TODO: combine identical states in previous column/prune dead end states
			removeStates(statesToRemove, delta, stateDictionary, stateInfo);
			if (delta.isEmpty()) {
				throw new RuntimeException("Unsatisiable. No sequence of length " + i + " can be produced given constraints");
			}
		}
		
		// establish accepting states
		for (Map<Map<Integer, Integer>, Integer> stateDictionaryAtFinalCol : stateDictionary.get(matchConstraintList.length).values()) {
			for (Integer finalColStateIdx : stateDictionaryAtFinalCol.values()) {
				acceptingStates.add(finalColStateIdx);
			}
		}
		
		return new Automaton<T>(markovModel.stateIndex,delta,acceptingStates);
	}

	private static void removeStates(TreeSet<Integer> statesToRemove, Map<Integer, Map<Integer, Integer>> delta,
			Map<Integer, Map<Integer, Map<Map<Integer, Integer>, Integer>>> stateDictionary, Map<Integer, Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>> stateInfo) {
		
		while(!statesToRemove.isEmpty()) {
			Integer nextStateToRemove = statesToRemove.pollLast();
			// remove q' from stateInfo
			Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>> stateToEdgesAndConstraints = stateInfo.remove(nextStateToRemove);
			Integer i = stateToEdgesAndConstraints.getFirst();
			Map<Integer, Set<Integer>> stateToEdges = stateToEdgesAndConstraints.getSecond();
			Map<Integer, Integer> constraints = stateToEdgesAndConstraints.getThird();
			
			// for each edge q,a going to the state q'
			for (Integer qIdx : stateToEdges.keySet()) {
				for (Integer aIdx : stateToEdges.get(qIdx)) {
					// remove q,a -> q' from delta 
					Utils.removeKeys(delta, qIdx, aIdx);
					// and remove i,a,constraints from stateDictionary
					Utils.removeKeys(stateDictionary, i, aIdx, constraints);
					// if q has no outgoing edges (meaning it would have been removed by the Utils.removeKeys command), mark it for removal
					if (!delta.containsKey(qIdx)) {
						statesToRemove.add(qIdx);
					}
				}
			}
		}
	}

	private static <T extends Token> int[] computeEquivalenceClass(BidirectionalVariableOrderPrefixIDMap<T> stateIndex) {
		
		List<T> equivalenceClassRepresentatives = new ArrayList<T>();
		
		List<LinkedList<T>> idToPrefixMap = stateIndex.getIDToPrefixMap();		
		int[] equivalenceClassMap = new int[idToPrefixMap.size()];
		
		for (int tokenId = 0; tokenId < idToPrefixMap.size(); tokenId++) {
			T token = idToPrefixMap.get(tokenId).getLast();
			boolean classFound = false;
			for (int equivalenceClassId = 0; equivalenceClassId < equivalenceClassRepresentatives.size(); equivalenceClassId++) {
				T representative = equivalenceClassRepresentatives.get(equivalenceClassId);
				if (token instanceof SyllableToken) {
					if (rhyme((SyllableToken) token, (SyllableToken) representative)) {
						equivalenceClassMap[tokenId] = equivalenceClassId;
						classFound = true;
						break;
					}
				} else if (token.equals(representative)) { // this needs to be adjusted according to the equivalence relation
					equivalenceClassMap[tokenId] = equivalenceClassId;
					classFound = true;
					break;
				}
			}
			
			if (!classFound) {
				equivalenceClassMap[tokenId] = equivalenceClassRepresentatives.size();
				equivalenceClassRepresentatives.add(token);
			}
		}
		
		
		return equivalenceClassMap;
	}
	
	private static boolean rhyme(SyllableToken syl1Token, SyllableToken syl2Token) {
		Syllable s1 = Syllabifier.tokenToSyllable(syl1Token);
		Syllable s2 = Syllabifier.tokenToSyllable(syl2Token);
		return (!s1.getOnset().equals(s2.getOnset()) && s1.getNucleus().equals(s2.getNucleus()) && s1.getCoda().equals(s2.getCoda()));
	}

	public static void main(String[] args) throws UnsatisfiableConstraintSetException, InterruptedException {
//		runExample1();
		runExample2(); // test for combining states
	}

	private static void runExample1() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"dead bear with Ed with bed hair"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
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
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{4,5,-1,-1,-1};
		int length = matchConstraintList.length;
		
		Automaton<SyllableToken> A = build(matchConstraintList, M);
		
		final ArrayList<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<List<ConditionedConstraint<SyllableToken>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<SyllableToken>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
	
	private static void runExample2() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"red is dead", "red was dead"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
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
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,-1}; // 1-based
		int length = matchConstraintList.length;
		
		Automaton<SyllableToken> A = build(matchConstraintList, M);
		
		final ArrayList<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<List<ConditionedConstraint<SyllableToken>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<SyllableToken>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
}
