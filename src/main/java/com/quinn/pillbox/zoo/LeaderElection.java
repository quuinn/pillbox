package com.quinn.pillbox.zoo;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import com.quinn.pillbox.feature.OnElectionCallback;

/**
 * 實作節點選舉
 * 
 * @author Quinn
 * @date Apr 8, 2022 6:18:30 AM
 */
public class LeaderElection implements Watcher {

	private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
	private static final int SESSION_TIMEOUT = 3000;
	private static final String ELECTION_NAMESPACE = "/election";
	private String currentZodeName;
	private final ZooKeeper zooKeeper;
	private final OnElectionCallback onElectionCallBack;

	public LeaderElection(ZooKeeper zooKeeper, OnElectionCallback onElectionCallback) {
		this.zooKeeper = zooKeeper;
		this.onElectionCallBack = onElectionCallback;
	}

	/**
	 * @Title: volunteerForLeadership
	 * @Description: 創建與初始化 znodes 於 tree
	 * @throws KeeperException
	 * @throws InterruptedException
	 * @return void 返回型別
	 * @throws
	 */
	public void volunteerForLeadership() throws KeeperException, InterruptedException {
		String znodePrefix = ELECTION_NAMESPACE + "/c_";

		// 1.OPEN_ACL_UNSAFE: ACL(Access Control List), 設定可以對自己存取的白名單(IP list)
		// 2.CreateMode.EPHEMERAL_SEQUENTIAL:
		// 2.1.EPHEMERAL: 當 client 因任何形式與 znode server disconnected 時, EPHEMERAL
		// 節點將會被刪除
		// 2.1.1.透過 EPHEMERAL 失聯及刪除與 watcher 隨時監控的特性, 作到節點與領導的控制
		// 2.2.SEQUENTIAL: 節點命名方式, 單純以 order 累計
		String znodeFullPath = zooKeeper.create(znodePrefix, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE,
				CreateMode.EPHEMERAL_SEQUENTIAL);
		System.out.println("znode name" + znodeFullPath);
		this.currentZodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
	}

	/**
	 * @Title: reElectLeader
	 * @Description: 節點選舉演算法
	 * @return void
	 * @throws KeeperException
	 * @throws InterruptedException
	 */
	public void reElectLeader() throws KeeperException, InterruptedException {
		String predecessorName = "";
		Stat predecessorStat = null;
		while (predecessorStat == null) {

			List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);

			// 1. 對 children list 做 natural ordering
			Collections.sort(children);

			// 2. 取得 "最小" 節點
			String smallestChild = children.get(0);

			// 3. 判斷自己是否為 "最小", 是則設定為 leader 節點, 若不是則依舊維持為一般節點
			if (smallestChild.equals(currentZodeName)) {
				System.out.println("I am the leader");
				onElectionCallBack.onElectedToBeLeader();
				return;
			} else {
				// 當currentZode 不是 smallestChild 時, 代表 currentZode 前面一定還有 node
				System.out.println("I am not the leader");
				int predecessorIndex = Collections.binarySearch(children, currentZodeName) - 1;
				predecessorName = children.get(predecessorIndex);

				// 透過 zookeeper.exists() 將 watcher 設定為監視 predecessorNode
				predecessorStat = zooKeeper.exists(ELECTION_NAMESPACE + "/" + predecessorName, this);
			}
		}
		onElectionCallBack.onWorker();
		System.out.println("Watching zode " + predecessorName);
		System.out.println();

	}

	/**
	 * @Title: process
	 * @Description: 依照不同的狀態處理
	 * @return void
	 * @throws KeeperException
	 * @throws InterruptedException
	 */
	@SuppressWarnings("incomplete-switch")
	@Override
	//
	public void process(WatchedEvent event) {
		// event trigger
		switch (event.getType()) {
		case NodeDeleted:
			try {
				reElectLeader();
			} catch (InterruptedException e) {
			} catch (KeeperException e) {
			}
		}

	}

	public void close() throws InterruptedException {
		zooKeeper.close();
	}

	public void run() throws InterruptedException {
		synchronized (zooKeeper) {
			zooKeeper.wait();
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException, KeeperException {

	}

}
