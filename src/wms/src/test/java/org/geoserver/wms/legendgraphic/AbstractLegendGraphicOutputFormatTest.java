/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.legendgraphic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.media.jai.PlanarImage;
import javax.xml.namespace.QName;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.wms.GetLegendGraphic;
import org.geoserver.wms.GetLegendGraphicRequest;
import org.geoserver.wms.WMSTestSupport;
import org.geoserver.wms.map.ImageUtils;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.feature.type.AttributeTypeImpl;
import org.geotools.feature.type.GeometryDescriptorImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.geotools.referencing.CRS;
import org.geotools.resources.coverage.FeatureUtilities;
import org.geotools.resources.image.ImageUtilities;
import org.geotools.styling.Rule;
import org.geotools.styling.SLDParser;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.util.logging.Logging;
import org.junit.After;
import org.junit.Before;
import org.opengis.coverage.grid.GridCoverage;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;


/**
 * Tets the functioning of the abstract legend producer for raster formats, which relies on
 * Geotools' StyledShapePainter.
 * 
 * @author Gabriel Roldan
 * @version $Id$
 */
public class AbstractLegendGraphicOutputFormatTest extends WMSTestSupport {

    private static final Logger LOGGER = Logging
            .getLogger(AbstractLegendGraphicOutputFormatTest.class);

    private BufferedImageLegendGraphicBuilder legendProducer;

    GetLegendGraphic service;

    
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        Catalog catalog = getCatalog();
        testData.addRasterLayer(new QName("http://www.geo-solutions.it", "world", "gs")
            , "world.tiff", "tiff", new HashMap(), MockData.class,catalog);
        testData.addStyle("rainfall",MockData.class,catalog);
        testData.addStyle("rainfall_ramp",MockData.class,catalog);
        testData.addStyle("rainfall_classes",MockData.class,catalog);
    }
    
    @Before
    public void setLegendProducer() throws Exception {
        this.legendProducer = new BufferedImageLegendGraphicBuilder() {
            public String getContentType() {
                return "image/png";
            }

        };

        service = new GetLegendGraphic(getWMS());
    }

    @After 
    public void resetLegendProducer() throws Exception {
        this.legendProducer = null;
    }

    /**
     * Tests that a legend is produced for the explicitly specified rule, when the FeatureTypeStyle
     * has more than one rule, and one of them is requested by the RULE parameter.
     */
    @org.junit.Test
    public void testUserSpecifiedRule() throws Exception {
        // load a style with 3 rules
        Style multipleRulesStyle = getCatalog().getStyleByName(
                MockData.ROAD_SEGMENTS.getLocalPart()).getStyle();
        assertNotNull(multipleRulesStyle);

        Rule rule = multipleRulesStyle.getFeatureTypeStyles()[0].getRules()[0];
        LOGGER.info("testing single rule " + rule.getName() + " from style "
                + multipleRulesStyle.getName());

        GetLegendGraphicRequest req = new GetLegendGraphicRequest();
        FeatureTypeInfo ftInfo = getCatalog().getFeatureTypeByName(
                MockData.ROAD_SEGMENTS.getNamespaceURI(), MockData.ROAD_SEGMENTS.getLocalPart());
        req.setLayer(ftInfo.getFeatureType());
        req.setStyle(multipleRulesStyle);
        req.setRule(rule.getName());
        req.setLegendOptions(new HashMap());

        final int HEIGHT_HINT = 30;
        req.setHeight(HEIGHT_HINT);

        // use default values for the rest of parameters
        this.legendProducer.buildLegendGraphic(req);

        BufferedImage image = this.legendProducer.buildLegendGraphic(req);

        // was the legend painted?
        assertNotBlank("testUserSpecifiedRule", image, LegendUtils.DEFAULT_BG_COLOR);

        // was created only one rule?
        String errMsg = "expected just one legend of height " + HEIGHT_HINT + ", for the rule "
                + rule.getName();
        int resultLegendCount = image.getHeight() / HEIGHT_HINT;
        assertEquals(errMsg, 1, resultLegendCount);
    }

    /**
     * Tests that a legend is produced for the explicitly specified rule, when the FeatureTypeStyle
     * has more than one rule, and one of them is requested by the RULE parameter.
     * 
     */
    @org.junit.Test
    public void testRainfall() throws Exception {
        // load a style with 3 rules
        Style multipleRulesStyle = getCatalog().getStyleByName("rainfall").getStyle();

        assertNotNull(multipleRulesStyle);

        GetLegendGraphicRequest req = new GetLegendGraphicRequest();
        CoverageInfo cInfo = getCatalog().getCoverageByName("world");
        assertNotNull(cInfo);

        GridCoverage coverage = cInfo.getGridCoverage(null, null);
        try {
            SimpleFeatureCollection feature;
            feature = FeatureUtilities.wrapGridCoverage((GridCoverage2D) coverage);
            req.setLayer(feature.getSchema());
            req.setStyle(multipleRulesStyle);
            req.setLegendOptions(new HashMap());
            
            final int HEIGHT_HINT = 30;
            req.setHeight(HEIGHT_HINT);
            
            // use default values for the rest of parameters
            this.legendProducer.buildLegendGraphic(req);

            BufferedImage image = this.legendProducer.buildLegendGraphic(req);

            // was the legend painted?
            assertNotBlank("testRainfall", image, LegendUtils.DEFAULT_BG_COLOR);
            
            // was the legend painted?
            assertNotBlank("testRainfall", image, LegendUtils.DEFAULT_BG_COLOR);
        } finally {
            RenderedImage ri = coverage.getRenderedImage();
            if(coverage instanceof GridCoverage2D) {
                ((GridCoverage2D) coverage).dispose(true);
            }
            if(ri instanceof PlanarImage) {
                ImageUtilities.disposePlanarImageChain((PlanarImage) ri);
            }
        }

    }

    /**
     * Tests that the legend graphic is still produced when the request's strict parameter is set to
     * false and a layer is not specified
     */
    @org.junit.Test
    public void testNoLayerProvidedAndNonStrictRequest() throws Exception {
        Style style = getCatalog().getStyleByName("rainfall").getStyle();
        assertNotNull(style);

        GetLegendGraphicRequest req = new GetLegendGraphicRequest();
        req.setStrict(false);
        req.setLayer(null);
        req.setStyle(style);

        final int HEIGHT_HINT = 30;
        req.setHeight(HEIGHT_HINT);

        // use default values for the rest of parameters
        this.legendProducer.buildLegendGraphic(req);

        BufferedImage image = this.legendProducer.buildLegendGraphic(req);

        // was the legend painted?
        assertNotBlank("testRainfall", image, LegendUtils.DEFAULT_BG_COLOR);

        // was the legend painted?
        assertNotBlank("testRainfall", image, LegendUtils.DEFAULT_BG_COLOR);

    }
    /**
     * Tests that the legend graphic is produced for multiple layers
     */
    @org.junit.Test
    public void testMultipleLayers() throws Exception {              
        GetLegendGraphicRequest req = new GetLegendGraphicRequest();
        
        int titleHeight = getTitleHeight(req);
        
        FeatureTypeInfo ftInfo = getCatalog().getFeatureTypeByName(
                MockData.ROAD_SEGMENTS.getNamespaceURI(), MockData.ROAD_SEGMENTS.getLocalPart());
        List<FeatureType> layers=new ArrayList<FeatureType>();
        layers.add(ftInfo.getFeatureType());
        
        req.setLayers(layers);
        
        List<Style> styles=new ArrayList<Style>();
        styles.add(getCatalog().getStyleByName(
                MockData.ROAD_SEGMENTS.getLocalPart()).getStyle());
        req.setStyles(styles);
        
        this.legendProducer.buildLegendGraphic(req);

        BufferedImage image = this.legendProducer.buildLegendGraphic(req);

        // was the legend painted?
        assertNotBlank("testMultipleLayers", image, LegendUtils.DEFAULT_BG_COLOR);
        int height=image.getHeight();
        
        layers.add(ftInfo.getFeatureType());
        styles.add(getCatalog().getStyleByName(
                MockData.ROAD_SEGMENTS.getLocalPart()).getStyle());
        this.legendProducer.buildLegendGraphic(req);

        image = this.legendProducer.buildLegendGraphic(req);        
        
        // was the legend painted?
        assertNotBlank("testMultipleLayers", image, LegendUtils.DEFAULT_BG_COLOR);
        // with 2 layers we should have a legend at least 2 times taller (title + 2 layers)
        
        assertEquals(2*(height+titleHeight),image.getHeight());
        
        // first title
        assertPixel(image, 1, titleHeight/2, new Color(0,0,0));
        
        // first layer
        assertPixel(image, 10, 10+titleHeight, new Color(192,160,0));
        
        assertPixel(image, 10, 30+titleHeight, new Color(0,0,0));
        
        assertPixel(image, 10, 50+titleHeight, new Color(224,64,0));
        
        // second title
        assertPixel(image, 1, 60+titleHeight+titleHeight/2, new Color(0,0,0));
        
        // same colors for the second layer
        assertPixel(image, 10, 70+titleHeight*2, new Color(192,160,0));
        
        assertPixel(image, 10, 90+titleHeight*2, new Color(0,0,0));
        
        assertPixel(image, 10, 110+titleHeight*2, new Color(224,64,0));
        
    }
    
    
    /**
     * Tests that with forceTitles option off no title is rendered
     */
    @org.junit.Test
    public void testForceTitlesOff() throws Exception {        
        
        GetLegendGraphicRequest req = new GetLegendGraphicRequest();
        Map<String,String> options = new HashMap<String,String>();
        options.put("forceTitles", "off");
        req.setLegendOptions(options);
        FeatureTypeInfo ftInfo = getCatalog().getFeatureTypeByName(
                MockData.ROAD_SEGMENTS.getNamespaceURI(), MockData.ROAD_SEGMENTS.getLocalPart());
        List<FeatureType> layers=new ArrayList<FeatureType>();
        layers.add(ftInfo.getFeatureType());
        
        req.setLayers(layers);
        
        List<Style> styles = new ArrayList<Style>();
        styles.add(getCatalog().getStyleByName(
                MockData.ROAD_SEGMENTS.getLocalPart()).getStyle());
        req.setStyles(styles);
        
        this.legendProducer.buildLegendGraphic(req);

        BufferedImage image = this.legendProducer.buildLegendGraphic(req);

        // was the legend painted?
        assertNotBlank("testMultipleLayers", image, LegendUtils.DEFAULT_BG_COLOR);
        int height=image.getHeight();
        
        layers.add(ftInfo.getFeatureType());
        styles.add(getCatalog().getStyleByName(
                MockData.ROAD_SEGMENTS.getLocalPart()).getStyle());
        this.legendProducer.buildLegendGraphic(req);

        image = this.legendProducer.buildLegendGraphic(req);        
        
        // was the legend painted?
        assertNotBlank("testForceTitlesOff", image, LegendUtils.DEFAULT_BG_COLOR);
        
        
        assertEquals(2*height,image.getHeight());
                
        // first layer
        assertPixel(image, 10, 10, new Color(192,160,0));
        
        assertPixel(image, 10, 30, new Color(0,0,0));
        
        assertPixel(image, 10, 50, new Color(224,64,0));
                
        // same colors for the second layer
        assertPixel(image, 10, 70, new Color(192,160,0));
        
        assertPixel(image, 10, 90, new Color(0,0,0));
        
        assertPixel(image, 10, 110, new Color(224,64,0));
        
    }
    
    /**
     * Tests that the legend graphic is produced for multiple layers
     * with different style for each layer.
     */
    @org.junit.Test
    public void testMultipleLayersWithDifferentStyles() throws Exception {        
        GetLegendGraphicRequest req = new GetLegendGraphicRequest();
        
        int titleHeight = getTitleHeight(req);
        
        FeatureTypeInfo ftInfo = getCatalog().getFeatureTypeByName(
                MockData.ROAD_SEGMENTS.getNamespaceURI(), MockData.ROAD_SEGMENTS.getLocalPart());
        List<FeatureType> layers=new ArrayList<FeatureType>();
        layers.add(ftInfo.getFeatureType());
        layers.add(ftInfo.getFeatureType());
        req.setLayers(layers);
        
        List<Style> styles=new ArrayList<Style>();
        Style style1= getCatalog().getStyleByName(
                MockData.ROAD_SEGMENTS.getLocalPart()).getStyle();
        styles.add(style1);
        
        Style style2= getCatalog().getStyleByName(
                MockData.LAKES.getLocalPart()).getStyle();
        styles.add(style2);
        req.setStyles(styles);
        
        this.legendProducer.buildLegendGraphic(req);

        BufferedImage image = this.legendProducer.buildLegendGraphic(req);

        
        // first layer
        assertPixel(image, 10, 10+titleHeight, new Color(192,160,0));
        
        assertPixel(image, 10, 30+titleHeight, new Color(0,0,0));
        
        assertPixel(image, 10, 50+titleHeight, new Color(224,64,0));
        
        // different color (style) for the second layer
        assertPixel(image, 10, 70+titleHeight*2, new Color(64,64,192));

    }
    
    /**
     * Tests that the legend graphic is produced for multiple layers
     * with different style for each layer.
     */
    @org.junit.Test
    public void testMixedGeometry() throws Exception {
        GetLegendGraphicRequest req = new GetLegendGraphicRequest();
    
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("MIXEDGEOMETRY");
        builder.setNamespaceURI("test");
        builder.setDefaultGeometry("GEOMETRY");
        CoordinateReferenceSystem crs = CRS.decode("EPSG:4326");
        builder.setCRS(crs);
    
        GeometryFactory geometryFactory = new GeometryFactory();
    
        AttributeType at = new AttributeTypeImpl(new NameImpl("ID"), String.class,
                false, false, Collections.EMPTY_LIST, null, null);
        builder.add(new AttributeDescriptorImpl(at, new NameImpl("ID"), 0, 1,
                false, null));
    
        GeometryType gt = new GeometryTypeImpl(new NameImpl("GEOMETRY"),
                Geometry.class, crs, false, false, Collections.EMPTY_LIST, null,
                null);
    
        builder.add(new GeometryDescriptorImpl(gt, new NameImpl("GEOMETRY"), 0, 1,
                false, null));
    
        FeatureType fType = builder.buildFeatureType();
        List<FeatureType> layers = new ArrayList<FeatureType>();
        layers.add(fType);
    
        req.setLayers(layers);
    
        List<Style> styles = new ArrayList<Style>();
        req.setStyles(styles);
    
        StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory(null);
        SLDParser stylereader = new SLDParser(styleFactory, getClass().getResource(
                "MixedGeometry.sld"));
        Style[] style = stylereader.readXML();
    
        styles.add(style[0]);
    
        this.legendProducer.buildLegendGraphic(req);
    
        BufferedImage image = this.legendProducer.buildLegendGraphic(req);
    
        assertNotBlank("testMixedGeometry", image, LegendUtils.DEFAULT_BG_COLOR);
        
        // LineSymbolizer
        assertPixel(image, 10, 10, new Color(0,0,0));
        
        // PolygonSymbolizer
        assertPixel(image, 10, 30, new Color(0,0,255));
        
        // PointSymbolizer
        assertPixel(image, 10, 50, new Color(255,0,0));
    
    }
    
    private int getTitleHeight(GetLegendGraphicRequest req) {    
        final BufferedImage image = ImageUtils.createImage(req.getWidth(),
                req.getHeight(), (IndexColorModel) null, req.isTransparent());
        return getRenderedLabel(image, "TESTTITLE", req).getHeight();
    }
    
    private BufferedImage getRenderedLabel(BufferedImage image, String label,
            GetLegendGraphicRequest request) {
        Font labelFont = LegendUtils.getLabelFont(request);
        boolean useAA = LegendUtils.isFontAntiAliasing(request);
    
        final Graphics2D graphics = image.createGraphics();
        graphics.setFont(labelFont);
        if (useAA) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
        }
        return LegendUtils.renderLabel(label, graphics, request);
    }
}
