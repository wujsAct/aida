package mpi.experiment.reader;


public class ParallelDataFileReader extends AidaFormatCollectionReader{

	
	  public ParallelDataFileReader(String collectionPath, String fileName) {
		super(collectionPath, fileName);
	}

	
	public ParallelDataFileReader(String collectionPath, String fileName,
			CollectionReaderSettings crs) {
		super(collectionPath, fileName, crs);
	}


	@Override
	protected int[] getCollectionPartFromTo(CollectionPart cp) {
		// TODO Auto-generated method stub
		return null;
	}

}
