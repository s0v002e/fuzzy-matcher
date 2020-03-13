package com.intuit.fuzzymatcher.component;


import com.intuit.fuzzymatcher.domain.*;
import org.apache.commons.lang3.BooleanUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intuit.fuzzymatcher.domain.ElementType.*;

/**
 * <p>
 * Starts the Matching process by element level matching and aggregates the results back
 * This uses the ScoringFunction defined at each Document to get the aggregated Document score for matched Elements
 */
public class DocumentMatch {

    private final TokenRepo tokenRepo;

    public DocumentMatch() {
        tokenRepo = new TokenRepo();
    }

    /**
     * Executes matching of a document stream
     *
     * @param documents Stream of Document objects
     * @return Stream of Match of Document type objects
     */
    public Stream<Match<Document>> matchDocuments(Stream<Document> documents) {
        List<Match<Element>> elementMatches = new ArrayList<>();

        documents.forEach(document -> {
            Set<Element> elements = document.getPreProcessedElement();
            elements.forEach(element -> findElementMatches(elementMatches, element));

        });
        return rollupDocumentScore(elementMatches.parallelStream());
    }

    private Stream<Match<Document>> rollupDocumentScore(Stream<Match<Element>> matchElementStream) {

        Map<Document, Map<Document, List<Match<Element>>>> groupBy = matchElementStream
                .collect(Collectors.groupingBy(matchElement -> matchElement.getData().getDocument(),
                        Collectors.groupingBy(matchElement -> matchElement.getMatchedWith().getDocument())));

        return groupBy.entrySet().parallelStream().flatMap(leftDocumentEntry ->
                leftDocumentEntry.getValue().entrySet()
                        .parallelStream()
                        .flatMap(rightDocumentEntry -> {
                            List<Score> childScoreList = rightDocumentEntry.getValue()
                                    .stream()
                                    .map(d -> d.getScore())
                                    .collect(Collectors.toList());
                            // System.out.println(Arrays.toString(childScoreList.toArray()));
                            Match<Document> leftMatch = new Match<Document>(leftDocumentEntry.getKey(), rightDocumentEntry.getKey(), childScoreList);
                            if (BooleanUtils.isNotFalse(rightDocumentEntry.getKey().isSource())) {
                                Match<Document> rightMatch = new Match<Document>(rightDocumentEntry.getKey(), leftDocumentEntry.getKey(), childScoreList);
                                return Stream.of(leftMatch, rightMatch);
                            }
                            return Stream.of(leftMatch);
                        }))
                .filter(match -> match.getResult() > match.getData().getThreshold());
    }

    private void findElementMatches(List<Match<Element>> elementMatches, Element element ) {
        List<Token> tokens = element.getTokens().collect(Collectors.toList());

        tokenRepo.initializeElementScore(element);
        tokens.forEach(token -> {

            if (BooleanUtils.isNotFalse(element.getDocument().isSource())) {
                elementMatches.addAll(tokenRepo.getThresholdMatching(token));
            }
            tokenRepo.put(token);
        });
    }


    public static void main(String[] args) {
        System.out.println("test start");
        testLocal();
    }

    private static void testLocal() {
        List<Document> sourceData = new ArrayList<>();

        sourceData.add(new Document.Builder("S1")
                .addElement(new Element.Builder().setType(NAME).setValue("James Parker").createElement())
                .addElement(new Element.Builder().setType(ADDRESS).setValue("123 new st. Minneapolis MN").createElement())
                .createDocument());

        sourceData.add(new Document.Builder("S3")
                .addElement(new Element.Builder().setType(NAME).setValue("Bond").createElement())
                .addElement(new Element.Builder().setType(ADDRESS).setValue("546 Stevens ave, sarasota fl").createElement())
                .createDocument());

        sourceData.add(new Document.Builder("S4")
                .addElement(new Element.Builder().setType(NAME).setValue("William").createElement())
                .addElement(new Element.Builder().setType(ADDRESS).setValue("123 niger Street, dallas tx").createElement())
                .createDocument());

        sourceData.add(new Document.Builder("S2")
                .addElement(new Element.Builder().setType(NAME).setValue("James").createElement())
                .addElement(new Element.Builder().setType(ADDRESS).setValue("123 new Street, minneapolis blah").createElement())
                .createDocument());

        DocumentMatch documentMatch = new DocumentMatch();
        Stream<Match<Document>> matches =  documentMatch.matchDocuments(sourceData.stream());

        Map<Document, List<Match<Document>>> result = matches.collect(Collectors.groupingBy(Match::getData));
        result.entrySet().forEach(entry -> {
            entry.getValue().forEach(match -> {
                System.out.println("Data: " + match.getData() + " Matched With: " + match.getMatchedWith() + " Score: " + match.getScore().getResult());
            });
        });
    }

}
