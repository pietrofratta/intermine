package org.intermine.bio.like;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.intermine.Coordinates;
import org.intermine.objectstore.ObjectStore;

/**
 * Matrices() is used for the pre-calculation of a matrix, that includes all items, that every
 * gene has in common with every other gene in the dataset (based on one single aspect).
 * With this matrix another matrix is calculated, that contains the similarity between every gene.
 * The similarity is a rating from 0 to 100, where 0 (null) means "nothing in common"
 * and 100 means "totally alike (in this aspect)".
 * For the aspect types "count" and "presence" there is no matrix calculated with the common items.
 *
 * The matrices are rectangular, where both the first row and the first column contains
 * all gene IDs. That is to simplify the run time calculations: If you want to get the
 * similar Genes for one specific gene, you just have to read out one row; the one where
 * the gene ID is in the first column.
 *
 * All calculations are based on one single aspect!
 *
 * @author selma
 */
public final class Matrices
{
    // The gene ID is always in column zero
    private static final int SUBJECT_ID_COLUMN = 0;
    // For rectangular matrices the gene ID is also in row zero
    private static final int SUBJECT_ID_ROW = 0;
    private static final int MAX_RATING = 100;

    private Matrices() {
        // Don't.
    }

    /**
     * Overrides interface MatrixOperation.
     * Finds common related items between all genes. Stores these row-wise in the database.
     *
     * @param os InterMine object store
     * @param matrix containing all genes and their related items.
     * @param aspectNumber Which aspect are we looking at? Add this number to the name of the stored
     * object/row
     * @return a rectangular matrix (HashMap with x- and y-coordinates as keys) containing all
     * gene IDs and the ArrayLists of related items, that genes have in common.
     */
    public static Map<Coordinates, ArrayList<Integer>> findCommonItems(ObjectStore os,
            final Map<Coordinates, Integer> matrix, String aspectNumber) {
        return commonMatrixLoop(os, matrix, aspectNumber, new MatrixOperation() {

            @Override
            public void loopAction(Map<Coordinates, ArrayList<Integer>> newMatrix,
                    Map<Coordinates, Integer> matrix, Coordinates coordinatesOuterGeneID) {
                final Map<Integer, ArrayList<Integer>> commonToOuter =
                        new HashMap<Integer, ArrayList<Integer>>();

                int xCoordinateOuter = coordinatesOuterGeneID.getKey();

                for (Map.Entry<Coordinates, Integer> inner : matrix.entrySet()) {
                    int xCoordinate = inner.getKey().getKey();
                    // if inner is in the same row than the current outer gene ID
                    if (xCoordinate == xCoordinateOuter) {
                        for (Map.Entry<Coordinates, Integer> inner2 : matrix.entrySet()) {
                            // if inner2 has not the same coordinates than inner2
                            // and if the items (e.g. pathways) have the same ID
                            // -> save the items, they are common
                            if (coordinatesOuterGeneID != inner2.getKey()
                                    && inner.getValue().equals(inner2.getValue())) {
                                ArrayList<Integer> commonItems;
                                int xCoordinate2 = inner2.getKey().getKey();
                                // check, if the corresponding gene ID is already saved
                                if (!commonToOuter.containsKey(xCoordinate2)) {
                                    // if "no": create new list
                                    commonItems = new ArrayList<Integer>();
                                    commonToOuter.put(xCoordinate2, commonItems);
                                    commonItems.add(inner2.getValue());
                                } else {
                                    // if "yes": add the common item to the list
                                    commonItems = commonToOuter.get(xCoordinate2);
                                    commonItems.add(inner2.getValue());
                                }
                            }
                        }
                    }
                }

                // Transfer the information to the commonMat in the outer loop
                for (Map.Entry<Integer, ArrayList<Integer>> entry : commonToOuter.entrySet()) {
                    newMatrix.put(new Coordinates(xCoordinateOuter + 1, entry.getKey() + 1),
                            entry.getValue());
                }

            }
        });
    }

    /**
     * Overrides interface MatrixOperation.
     * Does the same like method findCommonItems but for the type "presence".
     *
     * @param os InterMine object store
     * @param matrix containing all genes and their related items.
     * @param aspectNumber Which aspect are we looking at? Add this number to the name of the stored
     * object/row
     * @return a rectangular matrix (HashMap with x- and y-coordinates as keys) containing all
     * gene IDs and the ArrayLists of related items, that genes have in common.
     */
    public static Map<Coordinates, ArrayList<Integer>> findCommonItemsPresence(ObjectStore os,
            final Map<Coordinates, Integer> matrix, String aspectNumber) {
        return commonMatrixLoop(os, matrix, aspectNumber, new MatrixOperation() {

            @Override
            public void loopAction(Map<Coordinates, ArrayList<Integer>> newMatrix,
                    Map<Coordinates, Integer> matrix, Coordinates coordinatesOuterGeneID) {
                int xCoordinateOuter = coordinatesOuterGeneID.getKey();

                for (final Map.Entry<Coordinates, Integer> inner : matrix.entrySet()) {
                    int xCoordinate = inner.getKey().getKey();
                    // if inner is not a gene ID and
                    // and if inner is in the same row than the current outer gene ID
                    // -> save the items, they are in common
                    if (xCoordinate != SUBJECT_ID_COLUMN && xCoordinate == xCoordinateOuter) {
                        ArrayList<Integer> commonItems;
                        // check, if the corresponding gene ID is already saved
                        if (!newMatrix.containsKey(new Coordinates(xCoordinateOuter + 1,
                                xCoordinateOuter + 1))) {
                            // if "no": create new list
                            commonItems = new ArrayList<Integer>();
                            newMatrix.put(new Coordinates(xCoordinateOuter + 1,
                                    xCoordinateOuter + 1), commonItems);
                            commonItems.add(inner.getValue());
                        }
                        else {
                            // if "yes": add the common item to the list
                            commonItems = newMatrix.get(new Coordinates(xCoordinateOuter + 1,
                                    xCoordinateOuter + 1));
                            commonItems.add(inner.getValue());
                        }
                    }
                }

            }
        });
    }

    /**
     * Calculates the result for findCommonItems and findCommonItemsPresence.
     * Performs the outer loop.
     *
     * @param os InterMine object store
     * @param matrix containing all genes and their related items.
     * Format: Its first column contains
     * the gene IDs, the other columns contain the related items (1 column for each unique item).
     * @param aspectNumber Which aspect are we looking at? Add this number to the name of the stored
     * object/row
     * @param operation containing the overridden loopAction code
     * @return a rectangular matrix (HashMap with x- and y-coordinates as keys) containing all
     * gene IDs and the ArrayLists of related items, that genes have in common.
     * Format: The first row and the first column contain the gene IDs, whereas coordinates (0,1)
     * and (1,0) are the same ID (also (0,2) and (2,0), and so on). The other rows and columns
     * contain the ArrayLists of the common related items. E.g. ArrayList of (3,5) contains common
     * related items of the genes (3,0) and (0,5).
     */
    private static Map<Coordinates, ArrayList<Integer>> commonMatrixLoop(ObjectStore os,
            Map<Coordinates, Integer> matrix, String aspectNumber, MatrixOperation operation) {
        // The rectangular matrix to return
        Map<Coordinates, ArrayList<Integer>> commonMat =
                new HashMap<Coordinates, ArrayList<Integer>>();
        Map<Coordinates, Integer> allGeneIds = new HashMap<Coordinates, Integer>();
        for (final Map.Entry<Coordinates, Integer> outer : matrix.entrySet()) {
            int xCoordinate = outer.getKey().getKey();
            int yCoordinate = outer.getKey().getValue();
            if (yCoordinate == SUBJECT_ID_COLUMN) {
                // Transfer the gene IDs and save in ArrayLists
//                ArrayList<Integer> geneInColumn = new ArrayList<Integer>();
                ArrayList<Integer> geneInRow = new ArrayList<Integer>();
//                commonMat.put(new Coordinates(SUBJECT_ID_ROW, xCoordinate + 1), geneInColumn);
                commonMat.put(new Coordinates(xCoordinate + 1, SUBJECT_ID_COLUMN), geneInRow);
//                geneInColumn.add(matrix.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));
                geneInRow.add(matrix.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));

                // Perform the loopAction to find common items (e.g. pathways) for each subject
                // (gene) of the outer loop
                operation.loopAction(commonMat, matrix, outer.getKey());
                String geneId = Integer.toString(outer.getValue());
                Storing.saveCommonMatToDatabase(os, commonMat, aspectNumber, geneId);

                // Calculate the similarity rating
                Map<Coordinates, Integer> countCommonMat = countCommonItemsCategory(commonMat);
//                if (outer.getValue() == 1112303) {
//                    System.out.print("\nnormMat:\n");
//                    for (int j = 0; j < 20; j++) {
//                        for (int k = 0; k < 30; k++) {
//                            System.out.print(commonMat.get(new Coordinates(j, k)) + " ");
//                        }
//                        System.out.print("\n");
//                    }
//                }
                commonMat = new HashMap<Coordinates, ArrayList<Integer>>();

                countCommonMat = normalise(countCommonMat);
                Storing.saveNormMatToDatabase(os, countCommonMat, aspectNumber, geneId);

//                if (outer.getValue() == 1112303) {
//                    System.out.print("\nnormMat:\n");
//                    for (int j = 0; j < 20; j++) {
//                        for (int k = 0; k < 30; k++) {
//                            System.out.print(countCommonMat.get(new Coordinates(j, k)) + " ");
//                        }
//                        System.out.print("\n");
//                    }
//                }

                // get a list of all gene Ids
                allGeneIds.put(new Coordinates(yCoordinate, xCoordinate + 1), outer.getValue());
            }
        }
        Storing.saveNormMatToDatabase(os, allGeneIds, aspectNumber, "ALL");
        return commonMat;
    }

    /**
     * Calculates the similarity ratings pairwise and for one aspect for the type "category".
     *
     * @param commonMat a rectangular matrix (HashMap with x- and y-coordinates as keys) containing
     * all gene IDs and the ArrayLists of related items, that genes have in common.
     * @return a rectangular matrix (HashMap with x- and y-coordinates as keys) containing all
     * gene IDs and pairwise similarity ratings between the genes.
     */
    public static Map<Coordinates, Integer> countCommonItemsCategory(
            Map<Coordinates, ArrayList<Integer>> commonMat) {
        Map<Coordinates, Integer> simMat = new HashMap<Coordinates, Integer>();

        for (Map.Entry<Coordinates, ArrayList<Integer>> entry : commonMat.entrySet()) {
            // Transfer the gene IDs
            int xCoordinate = entry.getKey().getKey();
            int yCoordinate = entry.getKey().getValue();
            if (yCoordinate == SUBJECT_ID_ROW) {
                simMat.put(entry.getKey(), entry.getValue().get(0));
            }
            else {
                // Save the number of common items
                simMat.put(entry.getKey(), entry.getValue().size());
            }
        }
        return simMat;
    }

    /**
     * Calculates the similarity ratings pairwise and for one aspect for the type "count".
     *
     * @param os InterMine object store
     * @param matrix containing all genes and their related items.
     * @param aspectNumber Which aspect are we looking at? Add this number to the name of the stored
     * object/row
     * @param aspectNumber
     */
    public static void findSimilarityCount(ObjectStore os, Map<Coordinates, Integer> matrix,
            String aspectNumber) {
        Map<Coordinates, Integer> countedItems = new HashMap<Coordinates, Integer>();
        Map<Coordinates, Integer> simMat = new HashMap<Coordinates, Integer>();
        int xCoordinate;
        int yCoordinate;
        int count;
        int rating;
        int xCoordinateInner;
        int yCoordinateInner;

        // Count the items (e.g. pathways) for each gene
        for (Map.Entry<Coordinates, Integer> entry : matrix.entrySet()) {
            xCoordinate = entry.getKey().getKey();
            yCoordinate = entry.getKey().getValue();
            if (yCoordinate == SUBJECT_ID_COLUMN) {
                countedItems.put(entry.getKey(), entry.getValue());
                count = 0;
                for (Map.Entry<Coordinates, Integer> entry2 : matrix.entrySet()) {
                    if (entry.getKey().getKey() == entry2.getKey().getKey()) {
                        count += 1;
                    }
                }
                // "count - 1" because the gene ID is not part of the items (e.g. pathways)
                countedItems.put(new Coordinates(xCoordinate, 1), count - 1);
            }
        }

        // Build a rectangular matrix
        for (Map.Entry<Coordinates, Integer> outer : countedItems.entrySet()) {
            xCoordinate = outer.getKey().getKey();
            yCoordinate = outer.getKey().getValue();
            // Transfer the gene IDs
            if (yCoordinate == SUBJECT_ID_COLUMN) {
//                simMat.put(new Coordinates(SUBJECT_ID_ROW, xCoordinate + 1),
//                        countedItems.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));
                simMat.put(new Coordinates(xCoordinate + 1, SUBJECT_ID_COLUMN),
                        countedItems.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));
            }
            else { // If outer contains counted item
                for (Map.Entry<Coordinates, Integer> inner : countedItems.entrySet()) {
                    xCoordinateInner = inner.getKey().getKey();
                    yCoordinateInner = inner.getKey().getValue();
                    // Only transfer non-zero items -> makes the simMat more sparse
                    if (yCoordinateInner == 1 && outer.getValue() != 0 && inner.getValue() != 0) {
                        // Row-wise normalisation
                        rating = Math.abs(MAX_RATING * inner.getValue()) / outer.getValue();
                        if (rating > MAX_RATING) {
                            rating = MAX_RATING;
                        }
                        simMat.put(new Coordinates(xCoordinate + 1,
                                xCoordinateInner + 1), rating);

                    }
                }
//                if (countedItems.get(new Coordinates(xCoordinate, 0)) == 1112303) {
//                    System.out.print("\nnormMat:\n");
//                    for (int j = 0; j < 30; j++) {
//                        for (int k = 0; k < 30; k++) {
//                            System.out.print(simMat.get(new Coordinates(j, k)) + " ");
//                        }
//                        System.out.print("\n");
//                    }
//                }

                String geneId = Integer.toString(countedItems.get(new Coordinates(xCoordinate, 0)));
                Storing.saveNormMatToDatabase(os, simMat, aspectNumber, geneId);
                simMat = new HashMap<Coordinates, Integer>();
            }
        }
    }

    /**
     * Calculates the similarity ratings pairwise and for one aspect for the type "presence".
     *
     * @param os InterMine object store
     * @param matrix containing all genes and their related items.
     * @param aspectNumber Which aspect are we looking at? Add this number to the name of the stored
     * object/row
     */
    public static void findSimilarityPresence(ObjectStore os, Map<Coordinates, Integer> matrix,
            String aspectNumber) {
        Map<Coordinates, Integer> hasMat = new HashMap<Coordinates, Integer>();
        Map<Coordinates, Integer> simMat = new HashMap<Coordinates, Integer>();

        for (Map.Entry<Coordinates, Integer> entry : matrix.entrySet()) {
            int xCoordinate = entry.getKey().getKey();
            int yCoordinate = entry.getKey().getValue();
            if (yCoordinate == SUBJECT_ID_COLUMN) {
                hasMat.put(entry.getKey(), entry.getValue());
                if (matrix.get(new Coordinates(xCoordinate, 1)) == null) {
                    hasMat.put(new Coordinates(xCoordinate, 1), 0);
                }
                else {
                    hasMat.put(new Coordinates(xCoordinate, 1), 1);
                }
            }
        }

        for (Map.Entry<Coordinates, Integer> entry : hasMat.entrySet()) {
            int xCoordinate = entry.getKey().getKey();
            int yCoordinate = entry.getKey().getValue();
            if (yCoordinate == 0) {
//                simMat.put(new Coordinates(SUBJECT_ID_ROW, xCoordinate + 1),
//                        hasMat.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));
                simMat.put(new Coordinates(xCoordinate + 1, SUBJECT_ID_COLUMN),
                        hasMat.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));
            }
            else {
                for (Map.Entry<Coordinates, Integer> inner : hasMat.entrySet()) {
                    int xCoordinateInner = inner.getKey().getKey();
                    int yCoordinateInner = inner.getKey().getValue();
//                    if (inner.getKey().getValue() == 1) {
                        if (inner.getValue().equals(entry.getValue())) {
                            simMat.put(new Coordinates(xCoordinate + 1,
                                    inner.getKey().getKey() + 1), MAX_RATING);
                        }
//                    }
                }

//                if (hasMat.get(new Coordinates(xCoordinate, 0)) == 1112303) {
//                    System.out.print("\nnormMat:\n");
//                    for (int j = 0; j < 30; j++) {
//                        for (int k = 0; k < 30; k++) {
//                            System.out.print(simMat.get(new Coordinates(j, k)) + " ");
//                        }
//                        System.out.print("\n");
//                    }
//                }

                String geneId = Integer.toString(hasMat.get(new Coordinates(xCoordinate, 0)));
                Storing.saveNormMatToDatabase(os, simMat, aspectNumber, geneId);
                simMat = new HashMap<Coordinates, Integer>();
            }
        }
    }

    /**
     * Normalises the input matrix row-wise.
     * E.g. given matrix:        ->   normalised matrix:
     *   -   gene1 gene2 gene3          -   gene1 gene2 gene3
     * gene1   5     2   null         gene1  5/5   2/5  null
     * gene2   2     2    1           gene2  2/2   2/2  1/2
     * gene3  null   1    3           gene3  null  1/3  3/3
     *
     * @param matrix containing all gene IDs and pairwise similarity ratings between the genes.
     * @return the input matrix normalised (with values between 0 and 100)
     */
    public static Map<Coordinates, Integer> normalise(Map<Coordinates, Integer> matrix) {
        Map<Coordinates, Integer> normMat = new HashMap<Coordinates, Integer>();
        for (Map.Entry<Coordinates, Integer> entry : matrix.entrySet()) {
            // Transfer the gene IDs
            int xCoordinate = entry.getKey().getKey();
            int yCoordinate = entry.getKey().getValue();
            if (yCoordinate == SUBJECT_ID_COLUMN) {
//                normMat.put(new Coordinates(SUBJECT_ID_ROW, xCoordinate),
//                        matrix.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));
                normMat.put(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN),
                        matrix.get(new Coordinates(xCoordinate, SUBJECT_ID_COLUMN)));
            }
            // Calculations for normalisation
            if (xCoordinate != SUBJECT_ID_ROW && yCoordinate != SUBJECT_ID_COLUMN) {
                normMat.put(entry.getKey(), entry.getValue() * MAX_RATING / matrix.get(
                                new Coordinates(xCoordinate, xCoordinate)));
            }
        }
        return normMat;
    }

    /**
     * Used in commonMatrixLoop.
     *
     * @author selma
     *
     */
    interface MatrixOperation
    {
        /**
        * Which parameter are needed in the inner loop.
        *
        * @param newMatrix a rectangular matrix (HashMap with x- and y-coordinates as keys)
        * containing gene IDs and the ArrayLists of related items, that genes have in common.
        * @param matrix containing all genes and their related items.
        * @param relationShip coordinates of a gene ID
        */
        void loopAction(Map<Coordinates, ArrayList<Integer>> newMatrix,
                Map<Coordinates, Integer> matrix, Coordinates relationShip);

    }
}
