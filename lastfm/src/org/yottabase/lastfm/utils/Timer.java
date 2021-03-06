package org.yottabase.lastfm.utils;

public class Timer {

	protected String timerName;

	protected long elapsedTime = 0;
	
	protected long latestStart = 0;
	
	public Timer() {
	}
	
	public Timer(String timerName) {
		this.timerName = timerName;
	}
	
	public Timer(String timerName, boolean autostart) {
		this.timerName = timerName;
		
		if(autostart) this.startOrRestart();
	}
	
	public String getTimerName() {
		return timerName;
	}
	
	public void setTimerName(String timerName) {
		this.timerName = timerName;
	}	
	
	public void startOrRestart() {
		this.latestStart = System.currentTimeMillis();
	}

	public void pause() {
		this.elapsedTime += System.currentTimeMillis() - this.latestStart;
	}
	
	public long getElapsedTime(){
		return this.elapsedTime;
	}
	
	public void pauseAndPrint() {
		this.pause();
		System.out.println(this);
	}
	
	public void print() {
		System.out.println(this);
	} 
	
	public String toString(){
		
		int seconds = (int) (this.elapsedTime / 1000) % 60 ;
		int minutes = (int) ((this.elapsedTime / (1000*60)) % 60);
		int hours   = (int) (this.elapsedTime / (1000*60*60));
		
		return String.format("%s: %dms [%dh %dm %ds]",
			this.timerName,
			this.elapsedTime,
			hours, minutes, seconds
		);
	}


}
