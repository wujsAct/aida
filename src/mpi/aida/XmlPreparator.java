package mpi.aida;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mpi.aida.config.settings.PreparationSettings;
import mpi.aida.data.Mention;
import mpi.aida.data.Mentions;
import mpi.aida.data.PreparedInput;
import mpi.aida.data.PreparedInputChunk;
import mpi.aida.preparation.documentchunking.DocumentChunker;
import mpi.aida.preparation.documentchunking.SingleChunkDocumentChunker;
import mpi.aida.preparation.mentionrecognition.FilterMentions;
import mpi.aida.preparation.mentionrecognition.FilterMentions.FilterType;
import mpi.tokenizer.data.Token;
import mpi.tokenizer.data.Tokens;
import mpi.tools.javatools.datatypes.Pair;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlPreparator {

  private static final String NEW_LINE = "\n";

  private static final String EMPTY_STR = " ";

  // Byte Order Mark - should be cleaned before parsing xml.
  private static final String BOM_IDENTIFIER = "^([\\W]+)<";

  private static final String XML_TAG_START = "<";

  /*
   * TEI related xml elements 
   */

  private static final String TEI_NDB = "ndb";

  // TEI attribute names
  private static final String TEI_ATTRIBUTE_TYPE = "type";

  private static final String TEI_ATTRIBUTE_SUBTYPE = "subtype";

  // private static final String TEI_ATTRIBUTE_REF = "n";

  // TEI element names
  private static final String TEI_ELEMENT_TEXT = "text";

  private static final String TEI_ELEMENT_BODY = "body";

  private static final String TEI_ELEMENT_DIV = "div";

  private static final String TEI_ELEMENT_PARA = "p";

  private static final String TEI_ELEMENT_CHOICE_ABBR = "abbr";

  private static final String TEI_ELEMENT_REF_TARGET = "ref";

  private static final String TEI_ELEMENT_PERSON_NAME = "persName";

  private static final String TEI_ELEMENT_SEGMENT = "seg";

  // Top level divs type names
  private static final String TEI_ENTRY_HEAD = "kopf";

  private static final String TEI_ENTRY_GENEAL = "geneal";

  private static final String TEI_ENTRY_LIFE = "leben";

  @SuppressWarnings("unused")
  private int fromPage = -1;

  @SuppressWarnings("unused")
  private int toPage = -1;

  @SuppressWarnings("unused")
  private Logger logger = LoggerFactory.getLogger(XmlPreparator.class);

  @SuppressWarnings("unused")
  private List<Element> lstPages;

  @SuppressWarnings("unused")
  private boolean pagesRemaining = false;

  @SuppressWarnings("unused")
  private int composedBlockCount = 0;

  @SuppressWarnings("unused")
  private int textBlockCount = 0;

  private static FilterMentions filterMention = new FilterMentions();

  private static Map<String, String> stringToGroundTruth = new HashMap<String, String>();

  public XmlPreparator(int from, int to) {
    fromPage = from;
    toPage = to;
  }

  public XmlPreparator() {

  }

  /*
   * TEI Related Methods
   */

  private static Element removeElement(Element parent, String elementToRemove, Namespace ns) {
    List<Element> children = parent.getChildren();
    if (children.isEmpty()) {
      return parent;
    }

    for (Element child : children) {
      removeElement(child, elementToRemove, ns);
    }

    switch (elementToRemove) {
      case TEI_ELEMENT_REF_TARGET:
        // if parent is name and ref type = "n"
        for (Element child : parent.getChildren()) {
          Element refChild = child.getChild(TEI_ELEMENT_REF_TARGET, ns);
          if (refChild == null) {
            //System.out.println("No Ref Child for : " + child.getName());
            continue;
          }

          String typeAttr = refChild.getAttributeValue("type");
          refChild.getParentElement().getTextTrim();
          refChild.getTextTrim();
          if (typeAttr != null && typeAttr.equalsIgnoreCase("n")) {
            //System.out.println("Store the target url : " + refChild.getAttributeValue("target"));
            Pattern pattern = Pattern.compile("\\W(.+) \\W");
            Matcher matcher = pattern.matcher(child.getValue());
            if (matcher.find()) {
              stringToGroundTruth.put(matcher.group(1), refChild.getAttributeValue("target"));
            }
          }
          child.removeChild(refChild.getName(), ns);                
        }
        //parent.removeChildren(elementToRemove, ns);
        break;
      case TEI_ELEMENT_CHOICE_ABBR:
        Element ele = parent.getChild(elementToRemove, ns);
        if (ele != null) {
          ele.setText(EMPTY_STR);
        }
        break;
      default:
        break;
    }
    return parent;
  }

  private static Element cleanup(Element parent, Namespace ns) {
    // removes "->" link symbol from text
    parent = removeElement(parent, TEI_ELEMENT_REF_TARGET, ns);
    // replaces <abbr> element of choice by empty str
    parent = removeElement(parent, TEI_ELEMENT_CHOICE_ABBR, ns);
    return parent;
  }

  private static String getInfo(Element element, String divType, Namespace ns) {
    StringBuffer info = new StringBuffer();
    Element para = element.getChild(TEI_ELEMENT_PARA, ns);
    boolean addNewLineAtEnd = true;
    
    switch (divType) {
      case TEI_ENTRY_HEAD:
        Element tmpEle = para.getChild(TEI_ELEMENT_PERSON_NAME, ns);
        if (tmpEle != null) {
          StringBuffer tmpNameBuff = new StringBuffer();
          //tmpNameBuff.append("Start : ");
          List<Element> children = tmpEle.getChildren();
          //size = 2 (fN, lN)
          for(int i=children.size()-1;i>=0;i--){
            tmpNameBuff.append(children.get(i).getValue());
            if(i!=0) {
              tmpNameBuff.append(EMPTY_STR);
            }
          }          
          info.append(tmpNameBuff.toString()).append(EMPTY_STR + ":");
        }
        tmpEle = para.getChild(TEI_ELEMENT_SEGMENT, ns);

        if (tmpEle != null) {
          info.append(cleanup(tmpEle, ns).getValue()).append(NEW_LINE);
        }
        break;
      case TEI_ENTRY_GENEAL:
      case TEI_ENTRY_LIFE:
        if (para == null) {
          info.append(cleanup(element, ns).getValue()).append(NEW_LINE);
        } else {
          info.append(cleanup(para, ns).getValue()).append(NEW_LINE);
        }
        break;
      default:
        addNewLineAtEnd = false;
        break;
    }

    if (addNewLineAtEnd) {
      info.append(NEW_LINE);
    }

    return info.toString();
  }

  private static String getCompleteBiographie(Element divEntry, Namespace ns) {
    StringBuffer sb = new StringBuffer();

    for (Element child : divEntry.getChildren()) {
      if (!child.getName().equals(TEI_ELEMENT_DIV) || !child.hasAttributes()) continue;

      Attribute attr = child.getAttribute(TEI_ATTRIBUTE_TYPE);
      if (attr != null) {
        sb.append(getInfo(child, attr.getValue(), ns));
      }

    }
    return sb.toString();
  }

  private static String cleanExtractedText(String extractedText) {
    extractedText = extractedText.replaceAll("[ ]+", " ");
    extractedText = extractedText.substring(0, extractedText.length() - 1);
    return extractedText;
  }

  /*
   *  Utility methods 
   */

  /**
   * Process the smallest alto xml unit <String> 
   * 
   * @param line <String CONTENT> xml element
   * @return a string constructed from <String CONTENT> xml element
   */
  private String processLine(Element line) {
    // String, SP , HYP
    StringBuffer content = new StringBuffer();
    List<Element> lstElements = line.getChildren();
    for (Element element : lstElements) {
      if ("String".equalsIgnoreCase(element.getName())) {
        content.append(element.getAttributeValue("CONTENT"));
      } else if ("SP".equalsIgnoreCase(element.getName())) {
        content.append(" ");
      } else if ("HYP".equalsIgnoreCase(element.getName())) {
        //FIXME check!
        content.append("");
      }
    }
    return content.toString();
  }

  /**
   * Extracts all the TextLine elements within the given text block and constructs a string.
   * 
   * @param block A TextBlock to be processed
   * @return String representation of the Text block (with lines of content)
   */
  private String processTextBlock(Element block) {
    StringBuffer textContent = new StringBuffer();
    List<Element> lstLines = block.getChildren("TextLine", block.getNamespace());
    for (Element line : lstLines) {
      textContent.append(processLine(line)).append("\n");
    }
    return textContent.toString();
  }

  /**
   * Process the page element to extract atomic string units from xml
   * 
   * @param page Alto <Page> Xml element 
   * @return a String representing the content page stripped of all xml elements
   */
  private String constructTextFromPageElement(Element page) {
    StringBuffer content = new StringBuffer();
    Element printSpace = page.getChild("PrintSpace", page.getNamespace());
    for (Element ele : printSpace.getChildren()) {
      if ("ComposedBlock".equalsIgnoreCase(ele.getName())) {
        composedBlockCount++;
        List<Element> lstChildren = ele.getChildren();
        for (Element child : lstChildren) {
          if ("TextBlock".equalsIgnoreCase(child.getName())) {
            textBlockCount++;
            content.append(processTextBlock(child));
          } else {
            // TextLine
            content.append(processLine(child)).append("\n");
          }
        }
      } else if ("TextBlock".equalsIgnoreCase(ele.getName())) {
        textBlockCount++;
        content.append(processTextBlock(ele));
      }
    }
    return content.toString();
  }

  /**
   * Removes any special characters encoded before the beginning of XML content
   * 
   * @param xmlContent XML string to be cleaned
   * @return XML string with all junk characters removed
   */
  private static String cleanXmlString(String xmlContent) {
    return xmlContent.replaceFirst(BOM_IDENTIFIER, XML_TAG_START);
  }
  
  private Element getXmlRootElement(String content) throws Exception {
    // Step 1: Clean XML to remove junk characters
    StringReader sr = new StringReader(cleanXmlString(content));
    SAXBuilder sBuilder = new SAXBuilder();

    // Step 2: Build XML object model
    Document xmlBook = sBuilder.build(sr);
    return xmlBook.getRootElement();
  }

  public String extractTeiText(String content) throws Exception {
    String result = "";
    Element root = getXmlRootElement(content);
    Element body = root.getChild(TEI_ELEMENT_TEXT, root.getNamespace()).getChild(TEI_ELEMENT_BODY, root.getNamespace());
    for (Element entry : body.getChildren()) {
      if (TEI_NDB.equalsIgnoreCase(entry.getAttributeValue(TEI_ATTRIBUTE_SUBTYPE))) {
        result = getCompleteBiographie(entry, root.getNamespace());
        return cleanExtractedText(result);
      }
    }

    return result;
  }

  /**
   * Parses the TEI xml format and extracts the biographie text
   * @param content
   * @param docId
   * @param prepSettings
   * @return
   * @throws Exception
   */
  public PreparedInput prepareTeiXml(String content, String docId, PreparationSettings prepSettings) throws Exception {
    stringToGroundTruth.clear();
    String text = extractTeiText(content);
    PreparedInput pInp = prepareInputData(text, docId, prepSettings);

    return pInp;
  }

  /**
   * Parses the ALTO xml format and process each page creating a prepared input.
   * @param contentTEI_ATTRIBUTE_TYPE
   * @param docId
   * @param prepSettings
   * @return
   * @throws Exception
   */
  @SuppressWarnings("unused")
  public PreparedInput prepareAltoXml(String content, String docId, PreparationSettings prepSettings) throws Exception {

    Element root = getXmlRootElement(content);
    Element layout = root.getChild("Layout", root.getNamespace());

    // Step 3: Process all extracted pages, generatin<ref target="http://www.deutsche-biographie.de/sfz11652.html" type="n">→</ref>g Prepared Input Chunks
    Tokens allTokens = new Tokens();
    Mentions allMentions = new Mentions();
    int lastTokenStartPos = 0;
    int lastTokenEndPos = 0;
    boolean isAnyPageProcessed = false;
    List<PreparedInputChunk> chunks = new ArrayList<PreparedInputChunk>();
    for (Element page : layout.getChildren("Page", layout.getNamespace())) {
      int pageNumber = Integer.parseInt(page.getAttributeValue("ID").substring(4));
      String text = constructTextFromPageElement(page);
      PreparedInput pInp = prepareInputData(text, docId, prepSettings);
      Tokens currPageTokens = pInp.getTokens();
      Mentions currPageMentions = pInp.getMentions();
      currPageTokens.setPageNumber(pageNumber);
      //pInp.getMentions().setPageNumber(pageNumber);      

      if (isAnyPageProcessed) {
        for (Token token : currPageTokens) {
          int currTokenStartPos = lastTokenEndPos + 1; // assuming the original end of previous token is taken care of
          int currTokenEndPos = currTokenStartPos + token.getOriginal().length() + token.getOriginalEnd().length();
          Token newToken = new Token(token.getStandfordId(), token.getOriginal(), token.getOriginalEnd(), currTokenStartPos, currTokenEndPos,
              token.getSentence(), token.getParagraph(), token.getPOS(), token.getNE());
          // UPDATE MENTION Mention mention = new Me
          if (currPageMentions.containsOffset(token.getBeginIndex())) {
            Mention mention = currPageMentions.getMentionForOffset(token.getBeginIndex());
            Mention newMention = new Mention(mention.getMention(), mention.getStartToken(), mention.getEndToken(), mention.getStartStanford(),
                mention.getEndStanford(), mention.getSentenceId());
            newMention.setCharOffset(newToken.getBeginIndex());
            newMention.setCharLength(mention.getCharLength());
            allMentions.addMention(newMention);
          }
          newToken.setPageNumber(token.getPageNumber());
          allTokens.addToken(newToken);
          lastTokenEndPos = currTokenEndPos;
        }
      } else {
        for (Token token : currPageTokens) {
          lastTokenStartPos = token.getBeginIndex();
          lastTokenEndPos = token.getEndIndex();
          allTokens.addToken(token);
          if (currPageMentions.containsOffset(token.getBeginIndex())) {
            allMentions.addMention(currPageMentions.getMentionForOffset(token.getBeginIndex()));
          }
        }
        isAnyPageProcessed = true;
      }
    }// end of loop over pages

    DocumentChunker docChunker = prepSettings.getDocumentChunker();
    PreparedInput fullPrepInp = docChunker.process(docId, allTokens, allMentions);

    return fullPrepInp;
  }

  private PreparedInput prepareInputData(String text, String docId, PreparationSettings settings) {
    Pair<Tokens, Mentions> tokensMentions = null;
    if (settings.getMentionsFilter().equals(FilterType.Manual)) {
      tokensMentions = filterMention.filter(docId, text, settings.getMentionsFilter(), false, settings.getLanguage());
    } else {
      tokensMentions = filterMention
          .filter(docId, text, settings.getMentionsFilter(), settings.isUseHybridMentionDetection(), settings.getLanguage());
    }

    // Drop mentions below min occurrence count.
    if (settings.getMinMentionOccurrenceCount() > 1) {
      dropMentionsBelowOccurrenceCount(tokensMentions.second, settings.getMinMentionOccurrenceCount());
    }

    //NOTE: calling settings.getChunker will create a page based chunker which is not required 
    DocumentChunker chunker = new SingleChunkDocumentChunker();
    PreparedInput preparedInput = chunker.process(docId, tokensMentions.first, tokensMentions.second);

    return preparedInput;
  }

  public static void dropMentionsBelowOccurrenceCount(Mentions docMentions, int minMentionOccurrenceCount) {
    TObjectIntHashMap<String> mentionCounts = new TObjectIntHashMap<String>();
    for (Mention m : docMentions.getMentions()) {
      mentionCounts.adjustOrPutValue(m.getMention(), 1, 1);
    }
    List<Mention> mentionsToRemove = new ArrayList<Mention>();
    for (Mention m : docMentions.getMentions()) {
      if (mentionCounts.get(m.getMention()) < minMentionOccurrenceCount) {
        mentionsToRemove.add(m);
      }
    }
    for (Mention m : mentionsToRemove) {
      docMentions.remove(m);
    }
  }

  public Map<String, String> getExtractedGroundTruth() {
    return stringToGroundTruth;
  }
}