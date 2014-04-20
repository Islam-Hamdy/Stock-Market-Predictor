package Default;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.write.WriteException;

public class Main {

	static String path;
	static int sheetNum;
	static String statPath = Global.StatFolderPath;
	static WriteExcel excel;
	static HashSet<String> currAvailableDays;

	public static void main(String[] args) throws Exception, WriteException {

		currAvailableDays = getAvailableDays("$AAPL");

		// 0 mean Twitter- 1 mean StockTwits- Data -2 Combined
		Global.files_to_run = Global.sheet_num[1];

		// preprocessUrlExpansion();
		if (Global.files_to_run == Global.sheet_num[0]) {
			// path = Global.twitterDataExpandedPath;
			path = Global.twitterDataPath;
			sheetNum = 0;
		} else if (Global.files_to_run == Global.sheet_num[1]) {
			path = Global.stockTwitDataPath;
			sheetNum = 1;
		} else {// combined
			path = Global.combinedDataPath;
			sheetNum = 2;
		}

		File statDir = new File(statPath);
		if (!statDir.exists())
			statDir.mkdir();

		File statusDir = new File(path);
		File[] folders = statusDir.listFiles();
		String[] featuresList = Helper.getFeaturesList();
		StatisticsTool tool;
		excel = new WriteExcel();
		System.out.println("Start ");
		excel.passFeatures(featuresList);
		HashSet<String> avCompanies = getAvailableCompanies();
		for (int i = 0; i < folders.length; i++) {
			System.out.println("Step #1 OPEN COMP ");
			String folderName = folders[i].getName();

			if (folders[i].isDirectory() && avCompanies.contains(folderName)) {

				System.out.println("____" + folderName + "_____");
				openExcelWriter(folderName);
				myComp[] f = getFileList(folderName);

				System.out.println("get file list");

				int start = excel.getRowsCnt() - Global.lag_var - 1;
				System.out.println("raws#cnt = "+excel.getRowsCnt());

				System.out.println("START FROM = " + start);
				System.out.println("get into");

				for (int j = start; j < f.length; j++) {

					if (f[j].file.isFile()) {
						System.out.println(f[j].file.getName());
						tool = new StatisticsTool(folderName,
								f[j].file.getName(),
								f[j].file.getAbsolutePath());
						tool.parseData();
						tool.addSimilarityNodes();
						tool.buildActivityFeatures();
						tool.buildGraphFeatures();

						double[] a = tool.getFeaturesValues();

						double sum = 0;

						for (int k = 0; k < a.length; k++) {
							sum += a[k];
						}

						if (a != null || sum > 1)
							excel.addNewDay(f[j].file.getName(), a, true);

					} else {
						throw new Exception(
								"Make sure companies directories contain only files.");
					}
				}
				System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
				excel.drawTables();
				excel.adddummyDaysAtEnd();
				excel.writeAndClose();
			}

		}
	}

	private static void preprocessUrlExpansion() throws InterruptedException {
		String sourcePath, destPath = null;

		if (Global.files_to_run == Global.sheet_num[0]) {
			sourcePath = Global.twitterDataPath;
			// destPath = Global.twitterDataExpandedPath;
		} else {
			sourcePath = Global.stockTwitDataPath;
			// destPath = Global.stockTwitDataExpandedPath;
		}

		File statusDir = new File(sourcePath);
		File[] folders = statusDir.listFiles();
		URLExpander urlExpander;
		for (int i = 0; i < folders.length; i++) {
			String folderName = folders[i].getName();
			if (folders[i].isDirectory()) {
				String destinationPath = destPath + "/" + folderName;
				File destDir = new File(destinationPath);
				if (!destDir.exists())
					destDir.mkdir();
				urlExpander = new URLExpander(sourcePath + "/" + folderName,
						destinationPath);
				// System.out.println(destinationPath);
				urlExpander.startURLExpander();
				while (!urlExpander.isTerminated())
					;
			}
		}
	}

	private static void openExcelWriter(String folderName) throws Exception {
		String statfilePath = statPath + "/" + folderName + ".xls";
		excel.setOutputFile(statfilePath, folderName);
		File statDir = new File(statfilePath);
		if (!statDir.exists()) {
			excel.createExcel();
		}
		excel.initializeExcelSheet(sheetNum);
	}

	private static myComp[] getFileList(String folderName) throws Exception {
		File dir = new File(path + "/" + folderName);
		File[] files = dir.listFiles();
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
		Date start = sdf.parse(Global.startDate);

		int n = 0;
		for (int j = 0; j < files.length; j++) {
			Date cur = sdf.parse(files[j].getName());

			if (currAvailableDays.contains(files[j].getName())
					&& cur.after(start))
				n++;
		}

		myComp[] f = new myComp[n];
		int index = 0;
		for (int j = 0; j < files.length; j++) {
			Date cur = sdf.parse(files[j].getName());

			if (currAvailableDays.contains(files[j].getName())
					&& cur.after(start))
				f[index++] = new myComp(files[j]);
		}
		Arrays.sort(f);

		return f;
	}

	static class myComp implements Comparable<myComp> {
		File file;
		DateFormat f = new SimpleDateFormat("dd-MM-yyyy");
		String name;

		public myComp(File f) {
			file = f;
			name = f.getName();
		}

		@Override
		public int compareTo(myComp o) {
			try {
				return f.parse(name).compareTo(f.parse(o.name));
			} catch (ParseException e) {
				return 0;
			}
		}

	}

	public static HashSet<String> getAvailableDays(String CompanyName)
			throws Exception {
		HashSet<String> hs = new HashSet<String>();
		File inputWorkbook = new File(Global.historyPath + CompanyName + ".xls");
		Workbook w;
		w = Workbook.getWorkbook(inputWorkbook);
		Sheet sheet = w.getSheet(0);
		for (int i = 1; i < sheet.getRows(); i++) {
			Cell cell = sheet.getCell(0, i);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			String s = cell.getContents();
			Date from = sdf.parse(s);
			SimpleDateFormat sdf2 = new SimpleDateFormat("dd-MM-yyyy");
			hs.add(sdf2.format(from));
		}
		return hs;
	}

	static HashSet<String> getAvailableCompanies() {
		File dir = new File(Global.historyPath);
		File[] files = dir.listFiles();
		HashSet<String> hs = new HashSet<>();
		for (int i = 0; i < files.length; i++) {
			hs.add(files[i].getName().replace(".xls", ""));
		}
		return hs;
	}

}
