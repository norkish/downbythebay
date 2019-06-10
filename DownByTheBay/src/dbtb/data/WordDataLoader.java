package dbtb.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.StopWatch;

import dbtb.linguistic.phonetic.Phoneticizer;
import dbtb.linguistic.phonetic.syllabic.WordSyllables;
import dbtb.linguistic.syntactic.Pos;
import dbtb.linguistic.syntactic.StanfordNlpInterface;
import dbtb.main.Main;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.Token;
import dbtb.utils.Pair;
import dbtb.utils.Utils;
import edu.stanford.nlp.util.CoreMap;

public class WordDataLoader {
	
//	private static final long MAX_TRAINING_SENTENCES = -1; // -1 for no limit - CAN'T USE THIS TO THROTTLE ANY MORE - requires syncing
	private static final long MAX_TOKENS_PER_SENTENCE = 30; // keeps Stanford NLP fast
	private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors()-1;
	private static final int DEBUG = 1; 
	private static final double MAX_MEMORY_FOR_BASE_MODEL = 95.;
	final private int BATCH_SIZE = 200;
	
	private static final boolean USE_DUMMY_DATA = false;
	private static final String datadir = "/Users/bodipaul/Archive/2017_BYU/ComputationalCreativity/data/COCA Text DB/";
	private static final Map<String, String> filePrefixMap = new HashMap<String,String>();
	static {
		filePrefixMap.put("spoken", "text_spoken_kde/w_spok_");
		filePrefixMap.put("fiction", "text_fiction_awq/w_fic_");
		filePrefixMap.put("magazine", "text_magazine_qrb/w_mag_");
	};
	
	public static String trainingSource = "fiction";
	
	private String[] TRAINING = new String[]{
			"iced cakes inside The Bake",
			"Have you seen a moose with a pair of new shoes?",
			"Have you ever seen a bear combing his hair?",
			"Have you ever seen a llama wearing polka dot pajamas?",
			"Have you ever seen a llama wearing pajamas?",
			"Have you ever seen a moose with a pair of new shoes?",
			"Have you ever seen a pirate that just ate a veggie diet?",
			"Have you ever seen a law drinking from a straw?"
//			"I'm a bear combin' his hair?",
//			"Why is it so weird to think about a llama wearing polka dot pajamas?",
//			"I have a llama wearing pajamas.",
//			"Have you a pirate that just ate a veggie diet?",
	};
	
	
	public class DataProcessor {

		private static final int LAST_FILE_NUMBER = 22;
		private String[][] trainingSentences;
		private int[] nextBatchIdxes;
		
		public DataProcessor() {
			this.trainingSentences = new String[USE_DUMMY_DATA?1:(LAST_FILE_NUMBER + 1)][];
			this.nextBatchIdxes = new int[USE_DUMMY_DATA?1:(LAST_FILE_NUMBER + 1)];
			loadNextFile(0);
		}
		
		private synchronized int getNextBatchStart(int source) {
			int nextBatchStart = nextBatchIdxes[source];
			nextBatchIdxes[source] += BATCH_SIZE;
			
			if (nextBatchStart > trainingSentences[source].length && source < trainingSentences.length-1 && trainingSentences[source+1] == null) {
				loadNextFile(source+1);
			}
			
			return nextBatchStart;
		}
		
		private void loadNextFile(int source) {

			String[] trainingSentencesFromFile;
			if (USE_DUMMY_DATA) {
				trainingSentencesFromFile = TRAINING ;
			}
		
			if (!USE_DUMMY_DATA) {
				StringBuilder str = new StringBuilder();
				try {
					final String fileName = datadir + filePrefixMap.get(trainingSource) + (source+1990) + ".txt";
					if (DEBUG > 0) System.out.println("Loading " + fileName + " for training");
					BufferedReader br = new BufferedReader(new FileReader(fileName));
					String currLine;
					while ((currLine = br.readLine()) != null) {
						str.append(currLine);
					}
					br.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				String fileContents = str.toString();
				fileContents = fileContents.replaceAll("##\\d+(?= )", "");
				fileContents = fileContents.replaceAll("-## ", "");
				fileContents = fileContents.replaceAll("<p> ", "");
				trainingSentencesFromFile = fileContents.split(" [[^a-zA-Z']+ ]+");
			}
			
			trainingSentences[source] = trainingSentencesFromFile;
		}

		boolean threadCurrentlySyncing = false;
		private synchronized boolean threadCurrentlySyncing() {
			if (threadCurrentlySyncing) {
				return true;
			} else {
				threadCurrentlySyncing = true;
				return false;
			}
		}

		int returnStatus = 0;
		public int process() throws InterruptedException {
			if (DEBUG > 0)System.out.println("Starting " + NUM_THREADS + " threads");
			System.gc();
			StopWatch watch = new StopWatch();
			
			watch.start();
			//start threads
			ThreadGroup tg = new ThreadGroup("all threads");
			for (int j = 0; j < NUM_THREADS; j++) {
				Thread t = new Thread(tg, new Runnable() {
					
					@Override
					public void run() {
						
						Map<Integer, Double> priorCountsForBatch = new HashMap<Integer, Double>();
						Map<Integer, Map<Integer, Double>> transitionCountsForBatch = new HashMap<Integer, Map<Integer, Double>>();
						BidirectionalVariableOrderPrefixIDMap<WordToken> prefixIDMapForBatch = new BidirectionalVariableOrderPrefixIDMap<WordToken>(order);

						int sentencesTrainedOnForBatch = 0;
						
						int currentSource = 0;
						
						int nextBatchStart = getNextBatchStart(currentSource);
						if (nextBatchStart > trainingSentences[currentSource].length) {
							currentSource++;
							if (currentSource < trainingSentences.length)
								nextBatchStart = getNextBatchStart(currentSource);
						}

						int nextBatchEnd;
						boolean threadReadyToSync = false;
						
						while((!threadReadyToSync || threadCurrentlySyncing()) && currentSource < trainingSentences.length && nextBatchStart < trainingSentences[currentSource].length) {
							nextBatchEnd = Math.min(nextBatchStart+BATCH_SIZE, trainingSentences[currentSource].length);
							if (DEBUG > 1)System.out.println(Thread.currentThread().getName() + " training on sentences " + nextBatchStart + " to " + (nextBatchEnd-1) + " from " + (currentSource + 1990) + " (MEM:" + Main.computePercentTotalMemoryUsed() + "%)");
							for (int i = nextBatchStart; i < nextBatchEnd; i++) {
								String trainingSentence = trainingSentences[currentSource][i];
								//start thread
								// get Word tokens for all unique pronunciations of the sentence
								List<WordToken> trainingSentenceTokens = convertToWordTokens(cleanSentence(trainingSentence));
								// if there was no valid pronunciation, skip it
								if (trainingSentenceTokens == null) continue;
								// for each pronunciation
								final double trainingWeight = 1.0;
								// Start synchronization
								if (trainingSentenceTokens.size() < order) continue;
								Integer fromTokenID, toTokenID;
								LinkedList<WordToken> prefix = new LinkedList<WordToken>(trainingSentenceTokens.subList(0, order));
								//TODO add string associated w/ prefix to set for Word2Vec
								fromTokenID = prefixIDMap.addPrefix(prefix);
								for (int j = order; j < trainingSentenceTokens.size(); j++ ) {
									prefix.removeFirst();
									prefix.addLast(trainingSentenceTokens.get(j));
									
									toTokenID = prefixIDMap.addPrefix(prefix);
									Utils.incrementValueForKeys(transitionCountsForBatch, fromTokenID, toTokenID, trainingWeight);
									Utils.incrementValueForKey(priorCountsForBatch, fromTokenID, trainingWeight); // we do this for every token 

									fromTokenID = toTokenID;
								}
								if (DEBUG > 1) System.out.println("sentencesTrainedOn:" + sentencesTrainedOn + " transitions.size()=" + transitions.size() + " prefixIDMap.getPrefixCount()=" + prefixIDMapForBatch.getPrefixCount());
								sentencesTrainedOnForBatch++;
							}
							
							if (returnStatus != 1 && Main.computePercentTotalMemoryUsed() > MAX_MEMORY_FOR_BASE_MODEL) {
								if (DEBUG > 1)System.out.println("Hit memory threshold of " + MAX_MEMORY_FOR_BASE_MODEL + " for base model training");
								returnStatus = 1;
							}
							if (returnStatus == 1) {
								threadReadyToSync = true;
							}
							
							nextBatchStart = getNextBatchStart(currentSource);
							if (nextBatchStart > trainingSentences[currentSource].length) {
								currentSource++;
								if (currentSource < trainingSentences.length)
									nextBatchStart = getNextBatchStart(currentSource);
							}
						}
						if (DEBUG > 1)System.out.println(Thread.currentThread().getName() + " finished training");
						incrementSentencesAndPronunciationsTrainedOn(sentencesTrainedOnForBatch);
						incrementTransitionsAndPriors(transitionCountsForBatch, priorCountsForBatch);
					}
				});
				t.start();
			}
			
			int activeThreadCount = tg.activeCount()*2;
			Thread[] threads = new Thread[activeThreadCount];
			tg.enumerate(threads);
			for (int j = 0; j < activeThreadCount; j++) {
				Thread t = threads[j];
				if (t!=null) {
					t.join();
				}
			}
			
			
			watch.stop();
			if (DEBUG > 1) System.out.println("Time training with " + NUM_THREADS + " threads: " + watch.getTime());
			System.gc();

			return returnStatus; // if 1 is returned, it means the file wasn't fully read.
		}
		
		private synchronized void incrementTransitionsAndPriors(
				BidirectionalVariableOrderPrefixIDMap<WordToken> prefixIDMapForBatch, Map<Integer, Map<Integer, Double>> transitionCountsForBatch,
				Map<Integer, Double> priorCountsForBatch) {
			
			if (DEBUG > 1) System.out.println("Synchronizing batch transitions and priors for thread " + Thread.currentThread().getName() + "... ");
			
			if (prefixIDMap.isEmpty()) { // if empty, just adopt this batch's model
				prefixIDMap = prefixIDMapForBatch;
				transitions = transitionCountsForBatch;
				priors = priorCountsForBatch;
			} else {
				Double prevCount, batchCount;
				LinkedList<WordToken> fromState, toState;
				Integer absoluteFromID, absoluteToID;
				Map<Integer, Double> batchTransitionsMap, aggregateTransitionsMap;
				
				// otherwise, iterate over the transitions for the batch
				for (Integer fromID : transitionCountsForBatch.keySet()) {
					batchTransitionsMap = transitionCountsForBatch.get(fromID);
					
					// get the state intended by the batch's from ID
					fromState = prefixIDMapForBatch.getPrefixForID(fromID);



					// translate that state to the ID assigned to that state in the aggregate prefixIDMap
					absoluteFromID = prefixIDMap.addPrefix(fromState);
					// lookup the transitions map in the aggregate model using that absolute ID
					aggregateTransitionsMap = transitions.get(absoluteFromID);
					
					// if no transitions exist for that from state (ID)
					if (aggregateTransitionsMap == null) {
						// initialize it and populate it (Can't copy because we have to translate all toIDs)
						aggregateTransitionsMap = new HashMap<Integer, Double>();
						transitions.put(absoluteFromID, aggregateTransitionsMap);
					} 
					
					// for each batch toID
					for(Integer toID : batchTransitionsMap.keySet()) {
						batchCount = batchTransitionsMap.get(toID);
						
						// get the state represented by the batch ID
						toState = prefixIDMapForBatch.getPrefixForID(toID);
						// lookup the absolute id from the aggregate prefixid map
						absoluteToID = prefixIDMap.addPrefix(toState);
						// lookup the previous account associated with this state (ID)
						prevCount = aggregateTransitionsMap.get(absoluteToID);
						
						// if no previous counts
						if (prevCount == null) {
							// just copy the count
							aggregateTransitionsMap.put(absoluteToID, batchCount);
						} else {
							// otherwise sum
							aggregateTransitionsMap.put(absoluteToID, prevCount + batchCount);
						}
					}
				}
				
				// for each batch id in the prior dataset
				for(Integer id : priorCountsForBatch.keySet()) {
					batchCount = priorCountsForBatch.get(id);
					
					// lookup the state represented by that id
					toState = prefixIDMapForBatch.getPrefixForID(id);
					// find the absolute ID from the aggregate prefixIDMap
					absoluteToID = prefixIDMap.addPrefix(toState);
					// get the prevCounts associated with this state (ID)
					prevCount = priors.get(absoluteToID);
					
					// if no previous counts
					if (prevCount == null) {
						// just copy the count
						priors.put(absoluteToID, batchCount);
					} else {
						// otherwise sum
						priors.put(absoluteToID, prevCount + batchCount);
					}
				}
			}
			threadCurrentlySyncing = false;
			if (DEBUG > 1)System.out.println("Synchronization Complete!");
		}

		/**
		 * Assumes global ids
		 * @param transitionCountsForBatch
		 * @param priorCountsForBatch
		 */
		private synchronized void incrementTransitionsAndPriors(Map<Integer, Map<Integer, Double>> transitionCountsForBatch,
				Map<Integer, Double> priorCountsForBatch) {
			
			if (DEBUG > 1)System.out.println("Synchronizing batch transitions and priors for thread " + Thread.currentThread().getName() + "... ");
			
			Double prevCount, batchCount;
			Map<Integer, Double> batchTransitionsMap, aggregateTransitionsMap;
			
			// otherwise, iterate over the transitions for the batch
			for (Integer fromID : transitionCountsForBatch.keySet()) {
				batchTransitionsMap = transitionCountsForBatch.get(fromID);
				
				// lookup the transitions map in the aggregate model using that ID
				aggregateTransitionsMap = transitions.get(fromID);
				
				// if no transitions exist for that from state (ID)
				if (aggregateTransitionsMap == null) {
					transitions.put(fromID, batchTransitionsMap);
				} else { 
					// for each batch toID
					for(Integer toID : batchTransitionsMap.keySet()) {
						batchCount = batchTransitionsMap.get(toID);
						
						// lookup the previous account associated with this state (ID)
						prevCount = aggregateTransitionsMap.get(toID);
						
						// if no previous counts
						if (prevCount == null) {
							// just copy the count
							aggregateTransitionsMap.put(toID, batchCount);
						} else {
							// otherwise sum
							aggregateTransitionsMap.put(toID, prevCount + batchCount);
						}
					}
				}
			}
			
			// for each batch id in the prior dataset
			for(Integer id : priorCountsForBatch.keySet()) {
				batchCount = priorCountsForBatch.get(id);
				
				// get the prevCounts associated with this state (ID)
				prevCount = priors.get(id);
				
				// if no previous counts
				if (prevCount == null) {
					// just copy the count
					priors.put(id, batchCount);
				} else {
					// otherwise sum
					priors.put(id, prevCount + batchCount);
				}
			}
			
			threadCurrentlySyncing = false;
			if (DEBUG > 1)System.out.println("Synchronization Complete!");
		}
	}
	

	public static StanfordNlpInterface nlp = new StanfordNlpInterface();

	public static class DataSummary<T extends Token> {
		/**
		 * This map serves to store all possible tokens seen in the data set. Representing
		 * each prefix as an integer saves time and space later on. Essentially each new token (after
		 * checking it is not already in the map) is added thus: int tokenID = statesByIndex.addPrefix(prefix), 
		 * where prefix is an object of type LinkedList<SyllableToken>.
		 * The integer value in the map is what is used as the key for both the priors and the transitions data
		 * structures.
		 */
		public BidirectionalVariableOrderPrefixIDMap<T> statesByIndex;
		
		/**
		 * The inner key k_1 and outer key k_2 are the prefix IDs (see description of statesByIndex) for the from- and to-tokens respectively.
		 * The value is the transition probability of going from k_1 to k_2 as learned empirically from the data.
		 * Only insert inner and outer keys for non-zero transition probabilities (i.e., the absence
		 * of a key is used to indicate a probability of 0). k_1 and k_2 are both sequences of tokens of length markovOrder.
		 */
		public Map<Integer, Map<Integer, Double>> transitions;

		public Map<Integer, Double> priors;

		public DataSummary(BidirectionalVariableOrderPrefixIDMap<T> statesByIndex, Map<Integer, Double> priors, Map<Integer, Map<Integer, Double>> transitions) {
			this.statesByIndex = statesByIndex;
			this.transitions = transitions;
			this.priors = priors;
		}
	}

	private int order;
	private BidirectionalVariableOrderPrefixIDMap<WordToken> prefixIDMap;
	private Map<Integer, Double> priors = new HashMap<>();
	private Map<Integer, Map<Integer, Double>> transitions = new HashMap<>();
	private long sentencesTrainedOn = 0;
	
	public WordDataLoader(int markovOrder) {
		this.order = markovOrder;
		this.prefixIDMap = new BidirectionalVariableOrderPrefixIDMap<>(order);
	}

	public DataSummary<WordToken> loadData() throws InterruptedException {
		
		DataProcessor dp = new DataProcessor();
		int status = dp.process();
		
		System.out.println("Trained on " + sentencesTrainedOn + " sentences");

		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		DataSummary<WordToken> summary = new DataSummary<WordToken>(prefixIDMap, priors, transitions);
		return summary;
	}

	private synchronized void incrementSentencesAndPronunciationsTrainedOn(int sentencesTrainedOn) {
		this.sentencesTrainedOn += sentencesTrainedOn;
	}

	final private static String[] suffixes = new String[]{" n't "," ' "," 's "," 've ", " 'd ", " 'll ", " 're ", " 't "," 'm "};
	public String cleanSentence(String trainingSentence) {
		if (DEBUG > 1) System.out.println("CLEAN:" + trainingSentence);
		trainingSentence = " " + trainingSentence + " ";
		for (String suffix : suffixes) {
			if (trainingSentence.contains(suffix)) {
				return null;
			}
//			trainingSentence = trainingSentence.replaceAll(suffix, suffix.substring(1));
		}
		
		trainingSentence = trainingSentence.trim();
		
		if (trainingSentence.isEmpty()) {
			return null;
		}
		
		final int length = trainingSentence.split("\\s+").length;
		if (length > MAX_TOKENS_PER_SENTENCE){// || length < order) {
			return null;
		}
		
		return trainingSentence;
	}

	private static final boolean includePronunciation = false;
	public static List<WordToken> convertToWordTokens(String trainingSentence) {
		if (DEBUG > 1) System.out.println("CONVERT:" + trainingSentence);
		if (trainingSentence == null || trainingSentence.trim().isEmpty()) return null;
		List<CoreMap> taggedSentences = nlp.parseTextToCoreMaps(trainingSentence);
		List<Pair<String,Pos>> taggedWords = nlp.parseCoreMapsToPairs(taggedSentences.get(0));
		//TODO deal with instances where Stanford tagger splits words, like "don't" -> "do" + "n't"
//		final String[] words = trainingSentence.split("\\s+");

		List<WordToken> allTokensSentences = new ArrayList<>();
		// for every word
		for (Pair<String,Pos> taggedWord : taggedWords) {
			WordSyllables pronunciation = null;
			if (includePronunciation) {
				List<WordSyllables> pronunciations = Phoneticizer.syllableDict.get(taggedWord.getFirst().toUpperCase());
				if (pronunciations == null) {
					pronunciations = Phoneticizer.useG2P(taggedWord.getFirst().toUpperCase());
				}
				if (pronunciations == null || pronunciations.isEmpty()) return null;
	
				pronunciation = pronunciations.get(0); 
			}
			// create a new word token
			final WordToken newWordToken = new WordToken(taggedWord.getFirst(), pronunciation, taggedWord.getSecond());
			allTokensSentences.add(newWordToken);
		}
		return allTokensSentences;
	}

	public static void main(String[] args) {
		WordDataLoader dl = new WordDataLoader(4);
		convertToWordTokens(dl.cleanSentence("This is a sign of the decline of"));
	}

}
