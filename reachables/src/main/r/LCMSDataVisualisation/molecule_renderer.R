# Module for rendering molecule structures

kMolStructureCacheFolder <- "/home/thomas/data/mol-structure-cache/"

# Module server function
moleculeRenderer <- function(input, output, session, inchi, height) {
  
  inchi.string <- reactive({
    inchi <- inchi()
    shiny::validate(
      need(startsWith(inchi[1], "InChI="), "Should start with InChI")
    )
    inchi[1]
  })
  
  inchi.name <- reactive({
    inchi <- inchi()
    inchi[2]
  })
  
  imageFilepath <- reactive({
    inchiHash <- digest(inchi.string())
    filepath <- paste0(c(kMolStructureCacheFolder, inchiHash, ".png"), collapse = "")
    if (!file.exists(filepath)) {
      saveMoleculeStructure(inchi.string(), filepath)
    }
    filepath
  })
  
  output$molecule <- renderImage({
    list(src = imageFilepath(),
         contentType = "image/png",
         height = height,
         alt = "molecule")
  }, deleteFile = FALSE)
  
  output$molecule.name <- renderText({
    inchi.name <- inchi.name()
    # Ensure name is not empty
    if (!is.na(inchi.name)) {
      # Trim name if too long
      if (nchar(inchi.name) > 30) {
        paste0(strtrim(inchi.name, 30), "...")
      } else {
        inchi.name
      }
    }
  })
}

# Module UI function
moleculeRendererUI <- function(id) {
  ns <- NS(id)
  fluidRow(
    column(10, align="center",
           textOutput(ns("molecule.name")),
           imageOutput(ns("molecule"))  
    )
  )
}