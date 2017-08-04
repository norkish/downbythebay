package data;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;

import data.DataLoader.DataSummary;
import markov.BidirectionalVariableOrderPrefixIDMap;
import utils.Utils;

public class MusicDataLoader {
	
	private int markovOrder;
	
	public MusicDataLoader(int markovOrder) {
		this.markovOrder = markovOrder;
	}

	public DataSummary<RhythmToken> loadDummyData() {
		BidirectionalVariableOrderPrefixIDMap<RhythmToken> statesByIndex = new BidirectionalVariableOrderPrefixIDMap<>(markovOrder);
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer,Double>>();
		
		RhythmToken downBeat = new RhythmToken(1.0,0.0,false,0,0.,true, null);
		RhythmToken beat2 = new RhythmToken(1.0,1.0,false,0,0.,true, null);
		RhythmToken beat3 = new RhythmToken(1.0,2.0,false,0,0.,true, null);
		RhythmToken beat4 = new RhythmToken(1.0,3.0,false,0,0.,true, null);
		
		LinkedList<RhythmToken> prefix = new LinkedList<RhythmToken>();
		prefix.add(downBeat);
		prefix.add(beat2);
		prefix.add(beat3);
		prefix.add(beat4);
		Integer fromPrefixID = statesByIndex.addPrefix(prefix);
		Utils.incrementValueForKey(priors, fromPrefixID);
		
		prefix.removeFirst();
		prefix.addLast(downBeat);
		Integer toPrefixID = statesByIndex.addPrefix(prefix);
		Utils.incrementValueForKey(priors, toPrefixID);
		Utils.incrementValueForKeys(transitions, fromPrefixID, toPrefixID);
		
		for (int i = 0; i < 3; i++) {
			prefix.removeFirst();
			prefix.addLast(i==0?beat2:(i==1?beat3:beat4));
			fromPrefixID = toPrefixID;
			toPrefixID = statesByIndex.addPrefix(prefix);
			Utils.incrementValueForKey(priors, toPrefixID);
			Utils.incrementValueForKeys(transitions, fromPrefixID, toPrefixID);
		}
		
		return new DataSummary<RhythmToken>(statesByIndex, priors, transitions);
	}

	public DataSummary<RhythmToken> loadDummyData2() {
		BidirectionalVariableOrderPrefixIDMap<RhythmToken> statesByIndex = new BidirectionalVariableOrderPrefixIDMap<>(markovOrder);
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer,Double>>();
		
		RhythmToken[] beats = new RhythmToken[] {
				new RhythmToken(0.5,3.5,false,0,0., false, null), 
				new RhythmToken(1.,0.0,false,0,0., true, null),
				new RhythmToken(2.,1.0,false,0,0.,false, null),
				new RhythmToken(1.,3.0,false,0,0.,false, null),
				new RhythmToken(1.5,0.0,false,0,0.,true, null),
				new RhythmToken(0.5,1.5,false,0,0.,false, null),
				new RhythmToken(1.,2.0,false,0,0.,false, null),
				new RhythmToken(1.,3.0,false,0,0.,false, null),
				new RhythmToken(0.5,0.0,false,0,0.,true, null),
				new RhythmToken(1.5,0.5,false,0,0.,false, null),
		};
		
		
		LinkedList<RhythmToken> prefix = new LinkedList<RhythmToken>();
		for (int i = 0; i < markovOrder; i++) {
			prefix.add(beats[i]);
		}
		
		Integer fromPrefixID = statesByIndex.addPrefix(prefix);
		Utils.incrementValueForKey(priors, fromPrefixID);
		Integer toPrefixID;
		for (int i = markovOrder; i < beats.length; i++) {
			prefix.removeFirst();
			prefix.addLast(beats[i]);
			toPrefixID = statesByIndex.addPrefix(prefix);
			Utils.incrementValueForKey(priors, toPrefixID);
			Utils.incrementValueForKeys(transitions, fromPrefixID, toPrefixID);
			fromPrefixID = toPrefixID;
		}
		
		return new DataSummary<RhythmToken>(statesByIndex, priors, transitions);
	}

	private static final File[] files = new File("data/rhythm_data").listFiles();
	public DataSummary<RhythmToken> loadData() throws FileNotFoundException {
		BidirectionalVariableOrderPrefixIDMap<RhythmToken> statesByIndex = new BidirectionalVariableOrderPrefixIDMap<>(markovOrder);
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer,Double>>();
		
		int currComposition = 0;
		for (File file : files) {
			if (!file.getName().startsWith("John")) continue;
			System.out.println("For file " + file.getName());
			LinkedList<RhythmToken> prefix = new LinkedList<RhythmToken>();
			Integer fromPrefixID = null, toPrefixID;
			Scanner scan = new Scanner(file);
			String line;
			boolean tying = false, slurring = false;
			
			double currDurationInBeats = 0;
			double currBeatInMeasure = 0;
			int currMeasure = 0;
			int currPitch = 0;
			String currTime = null;
			
			int prevMeasure = -1;
			while(scan.hasNextLine()) {
				line = scan.nextLine();
				String[] split = line.split("\t");
				//measure + "\t" + beats + "\t" + musicXML.divsToBeats(note.duration, measure) + "\t" + note.pitch + "\t" + note.tie + "\t" + note.slur

				RhythmToken beat = null;
				int measure = Integer.parseInt(split[0]);
				double beatInMeasure = Double.parseDouble(split[1]);
				double durationInBeats = Double.parseDouble(split[2]);
				int pitch = Integer.parseInt(split[3]);
				String tieString = split[4];
				String slurString = split[5];
				String time = split[6];
				String lyric = split[7];
				
				if (tying) {
					assert(currPitch == pitch);
					currDurationInBeats += durationInBeats;
					if (tieString.equals("STOP")) {
						tying = false;
						beat = new RhythmToken(currDurationInBeats, currBeatInMeasure, currPitch == -2, currComposition, currMeasure, currBeatInMeasure == 0.0 || currMeasure != measure, currTime);
					} else {
						prevMeasure = measure;
						continue;
					}
				}

				if (tieString.equals("START")) {
					tying = true;
					currDurationInBeats = durationInBeats;
					currBeatInMeasure = beatInMeasure;
					currMeasure = measure;
					currPitch = pitch;
					currTime = time;
					// don't add note
					prevMeasure = measure;
					continue;
				} 

				if (slurring) {
					if (pitch != currPitch) {
						// add previous note
						beat = new RhythmToken(currDurationInBeats, currBeatInMeasure, currPitch == -2, currComposition, currMeasure, currBeatInMeasure == 0.0 || currMeasure != prevMeasure, currTime);
						fromPrefixID = addNote(statesByIndex, priors, transitions, prefix, fromPrefixID, beat);
						currDurationInBeats = durationInBeats;
						currBeatInMeasure = beatInMeasure;
						currMeasure = measure;
						currTime = time;
						currPitch = pitch;
					} else {
						currDurationInBeats += durationInBeats;
					}
					
					if (slurString.equals("STOP")) {
						slurring = false;
						beat = new RhythmToken(currDurationInBeats, currBeatInMeasure, currPitch == -2, currComposition, currMeasure, currBeatInMeasure == 0.0 || currMeasure != measure, currTime);
					} else {
						prevMeasure = measure;
						continue;
					}
				}

				if (slurString.equals("START")) {
					slurring = true;
					currDurationInBeats = durationInBeats;
					currBeatInMeasure = beatInMeasure;
					currMeasure = measure;
					prevMeasure = measure;
					currPitch = pitch;
					currTime = time;
					// don't add note
					continue;
				} 
				
				
				currDurationInBeats = Double.parseDouble(split[2]);
				
				if (beat == null) beat = new RhythmToken(durationInBeats,beatInMeasure,pitch == -2,currComposition, measure, beatInMeasure==0.0, time);
				
				fromPrefixID = addNote(statesByIndex, priors, transitions, prefix, fromPrefixID, beat);
				
			}
			currComposition++;
			System.out.println();
		}

		return new DataSummary<RhythmToken>(statesByIndex, priors, transitions);
	}

	private Integer addNote(BidirectionalVariableOrderPrefixIDMap<RhythmToken> statesByIndex, Map<Integer, Double> priors,
			Map<Integer, Map<Integer, Double>> transitions, LinkedList<RhythmToken> prefix, Integer fromPrefixID,
			RhythmToken beat) {
		
		System.out.print(beat + " ");
		Integer toPrefixID;
		if (prefix.size() == markovOrder - 1) {
			prefix.addLast(beat);
			fromPrefixID = statesByIndex.addPrefix(prefix);
		} else if (prefix.size() == markovOrder) {
			prefix.removeFirst();
			prefix.addLast(beat);
			toPrefixID = statesByIndex.addPrefix(prefix);
			Utils.incrementValueForKey(priors, toPrefixID);
			Utils.incrementValueForKeys(transitions, fromPrefixID, toPrefixID);
			fromPrefixID = toPrefixID;
		} else { 
			prefix.addLast(beat);
		}
		
		return fromPrefixID;
	}

}
