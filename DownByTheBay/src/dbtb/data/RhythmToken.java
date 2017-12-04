package dbtb.data;

import dbtb.markov.Token;

public class RhythmToken extends Token {
	
	double duration;
	public double getDuration() {
		return duration;
	}

	public double getOffsetFromDownbeat() {
		return offsetFromDownbeat;
	}

	public boolean isRest() {
		return rest;
	}

	public int getSourceComposition() {
		return sourceComposition;
	}

	public double getMeasureInSourceComposition() {
		return measureInSourceComposition;
	}

	double offsetFromDownbeat;
	boolean rest;
	int sourceComposition;
	double measureInSourceComposition;
	private boolean containsDownBeat;
	private String time;
	
	public RhythmToken(double duration, double offsetToDownbeat, boolean rest, int sourceComposition,
			double measureInSourceComposition, boolean containsDownBeat, String time) {
		this.duration = duration;
		this.offsetFromDownbeat = offsetToDownbeat;
		this.rest = rest;
		this.sourceComposition = sourceComposition;
		this.measureInSourceComposition = measureInSourceComposition;
		this.containsDownBeat = containsDownBeat;
		this.time = time;
	}

	
	
	public boolean containsDownBeat() {
		return containsDownBeat;
	}
	
	@Override
	public String toString() {
		return (containsDownBeat()?"*":"") + offsetFromDownbeat + "(" + measureInSourceComposition + ")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (containsDownBeat ? 1231 : 1237);
		long temp;
		temp = Double.doubleToLongBits(duration);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(offsetFromDownbeat);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((time == null) ? 0 : time.hashCode());
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
		RhythmToken other = (RhythmToken) obj;
		if (containsDownBeat != other.containsDownBeat)
			return false;
		if (Double.doubleToLongBits(duration) != Double.doubleToLongBits(other.duration))
			return false;
		if (Double.doubleToLongBits(offsetFromDownbeat) != Double.doubleToLongBits(other.offsetFromDownbeat))
			return false;
		if (time == null) {
			if (other.time != null)
				return false;
		} else if (!time.equals(other.time))
			return false;
		return true;
	}

	public String getTime() {
		return time;
	}
	
}
