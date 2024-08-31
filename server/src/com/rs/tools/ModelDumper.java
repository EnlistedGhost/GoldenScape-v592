package com.rs.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.alex.store.Index;
import com.alex.store.Store;
import com.rs.cache.Cache;

public class ModelDumper {

	public static void main(String[] args) throws IOException {
		Cache.STORE = new Store("C:/Users/RemoTwo/Desktop/592_matrix/Server592/data/cache/", false);
		Index index = Cache.STORE.getIndexes()[7];
		System.out.println(index.getLastArchiveId());
		for (int i = 0; i < index.getLastArchiveId(); i++) {
			byte[] data = index.getFile(i);
			if (data == null)
				continue;
			// if(!(data[data.length + -1] == -1 && data[-2 + data.length] ==
			// -1))
			// if((data[-1 + data.length] ^ 0xffffffff) != 0 || data[-2 +
			// data.length] != -1)
			// System.out.println(i);
			writeFile(data, "C:/Users/RemoTwo/Desktop/RS_Models/" + i + ".dat");
		}

	}

	public static void writeFile(byte[] data, String fileName) throws IOException {
		OutputStream out = new FileOutputStream(fileName);
		out.write(data);
		out.close();
	}

}
