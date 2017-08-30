/**
 * @Probject Name: netty-wfj-monitor
 * @Path: com.wfj.netty.monitor.dtoMemUsage.java
 * @Create By Jack
 * @Create In 2015年10月28日 下午2:52:09
 * TODO
 */
package com.jack.netty.servlet.dto;

/**
 * @Class Name MemUsage
 * @Author Jack
 * @Create In 2015年10月28日
 */
public class MemoryUsage {

	private long init;
	private long used;
	private long committed;
	private long max;

	
	public MemoryUsage() {
		super();
		this.init = 0;
		this.used = 0;
		this.committed = 0;
		this.max = 0;
	}

	/**
	 * @Return the long init
	 */
	public long getInit() {
		return init;
	}

	/**
	 * @Param long init to set
	 */
	public void setInit(long init) {
		this.init = init;
	}

	/**
	 * @Return the long used
	 */
	public long getUsed() {
		return used;
	}

	/**
	 * @Param long used to set
	 */
	public void setUsed(long used) {
		this.used = used;
	}

	/**
	 * @Return the long committed
	 */
	public long getCommitted() {
		return committed;
	}

	/**
	 * @Param long committed to set
	 */
	public void setCommitted(long committed) {
		this.committed = committed;
	}

	/**
	 * @Return the long max
	 */
	public long getMax() {
		return max;
	}

	/**
	 * @Param long max to set
	 */
	public void setMax(long max) {
		this.max = max;
	}

}
