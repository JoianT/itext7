package com.itextpdf.model.renderer;

import com.itextpdf.basics.LogMessageConstant;
import com.itextpdf.basics.PdfException;
import com.itextpdf.model.Property;
import com.itextpdf.model.layout.LayoutArea;
import com.itextpdf.model.layout.LayoutContext;
import com.itextpdf.model.layout.LayoutResult;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RootRenderer extends AbstractRenderer {

    protected boolean immediateFlush = true;
    protected LayoutArea currentArea;
    protected int currentPageNumber;

    public void addChild(IRenderer renderer) {
        super.addChild(renderer);

        if (currentArea == null) {
            currentArea = getNextArea(null);
        }

        // Static layout
        if (childRenderers.size() != 0 && childRenderers.get(childRenderers.size() - 1) == renderer) {
            List<IRenderer> resultRenderers = new ArrayList<>();
            LayoutResult result = null;

            LayoutArea storedArea = null;
            LayoutArea nextStoredArea = null;
            while (renderer != null && (result = renderer.layout(new LayoutContext(currentArea.clone()))).getStatus() != LayoutResult.FULL) {
                if (result.getStatus() == LayoutResult.PARTIAL) {
                    if (result.getOverflowRenderer() instanceof ImageRenderer) {
                        ((ImageRenderer) result.getOverflowRenderer()).autoScale(currentArea);
                    } else {
                        processRenderer(result.getSplitRenderer(), resultRenderers);
                        if (nextStoredArea != null) {
                            currentArea = nextStoredArea;
                            currentPageNumber = nextStoredArea.getPageNumber();
                            nextStoredArea = null;
                        } else {
                            getNextArea(result);
                        }
                    }
                } else if (result.getStatus() == LayoutResult.NOTHING) {
                    if (result.getOverflowRenderer() instanceof ImageRenderer) {
                        if (currentArea.getBBox().getHeight() < ((ImageRenderer) result.getOverflowRenderer()).imageHeight) {
                            getNextArea(result);
                        }
                        ((ImageRenderer)result.getOverflowRenderer()).autoScale(currentArea);
                    } else {
                        if (currentArea.isEmptyArea() && !(renderer instanceof AreaBreakRenderer)) {
                            if (Boolean.valueOf(true).equals(result.getOverflowRenderer().getModelElement().getProperty(Property.KEEP_TOGETHER))) {
                                result.getOverflowRenderer().getModelElement().setProperty(Property.KEEP_TOGETHER, false);
                                Logger logger = LoggerFactory.getLogger(DocumentRenderer.class);
                                logger.warn(LogMessageConstant.ELEMENT_DOES_NOT_FIT_AREA);
                                if (storedArea != null) {
                                    nextStoredArea = currentArea;
                                    currentArea = storedArea;
                                    currentPageNumber = storedArea.getPageNumber();
                                }
                                storedArea = currentArea;
                            } else {
                                result.getOverflowRenderer().setProperty(Property.FORCED_PLACEMENT, true);
                                Logger logger = LoggerFactory.getLogger(DocumentRenderer.class);
                                logger.warn(LogMessageConstant.ELEMENT_DOES_NOT_FIT_AREA);
                            }
                            renderer = result.getOverflowRenderer();

                            continue;
                        }
                        storedArea = currentArea;
                        getNextArea(result);
                    }
                }
                renderer = result.getOverflowRenderer();
            }
            currentArea.getBBox().setHeight(currentArea.getBBox().getHeight() - result.getOccupiedArea().getBBox().getHeight());
            currentArea.setEmptyArea(false);
            if (renderer != null) {
                processRenderer(renderer, resultRenderers);
            }

            childRenderers.remove(childRenderers.size() - 1);
            if (!immediateFlush) {
                childRenderers.addAll(resultRenderers);
            }
        } else if (positionedRenderers.size() > 0 && positionedRenderers.get(positionedRenderers.size() - 1) == renderer) {
            Integer positionedPageNumber = renderer.getProperty(Property.PAGE_NUMBER);
            if (positionedPageNumber == null)
                positionedPageNumber = currentPageNumber;
            renderer.layout(new LayoutContext(new LayoutArea(positionedPageNumber, currentArea.getBBox().clone())));

            if (immediateFlush) {
                flushSingleRenderer(renderer);
                positionedRenderers.remove(positionedRenderers.size() - 1);
            }
        }
    }

    // Drawing of content. Might need to rename.
    public void flush() {
        for (IRenderer resultRenderer: childRenderers) {
            flushSingleRenderer(resultRenderer);
        }
        for (IRenderer resultRenderer : positionedRenderers) {
            flushSingleRenderer(resultRenderer);
        }
        childRenderers.clear();
        positionedRenderers.clear();
    }

    @Override
    public LayoutResult layout(LayoutContext layoutContext) {
        throw new IllegalStateException("Layout is not supported for root renderers.");
    }

    protected abstract void flushSingleRenderer(IRenderer resultRenderer);

    protected abstract LayoutArea getNextArea(LayoutResult overflowResult);

    private void processRenderer(IRenderer renderer, List<IRenderer> resultRenderers) {
        alignChildHorizontally(renderer, currentArea.getBBox().getWidth());
        if (immediateFlush) {
            flushSingleRenderer(renderer);
        } else {
            resultRenderers.add(renderer);
        }
    }

}