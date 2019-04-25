#!/bin/env coffee
_ = require 'lodash'
fs        = require 'fs'
moment    = require 'moment'
program   = require 'commander'
yaml      = require 'js-yaml'

SET_YAML_VALUES = process.env.SET_YAML_VALUES

log = (message) ->
  timestamp = moment().format "YYYY-MM-DD HH:mm:ss"
  console.log "[#{timestamp}] [SET-YAML] #{message}"

fetchSetValues = (aJsonValues = null) ->
  jsonValues = null
  jsonStringValues = aJsonValues
  unless _.isString jsonStringValues
    if _.isString SET_YAML_VALUES
      log "use values from environment var [#{SET_YAML_VALUES}]"
      jsonStringValues = SET_YAML_VALUES
    else
      log "[Warning] no values"
  else
    log 'use values from script parameter (--values)'
  
  try
    jsonValues = JSON.parse jsonStringValues
          
  catch e
    jsonValues = null
    log '--- set-yaml error !! -- check SET_VALUES environment variable'
    log '--- message ---'
    log e
    log '---------------'
 
  jsonValues

modYaml = (infile, outfile, values) ->
  json = yaml.safeLoad fs.readFileSync infile

  for [jsonPath, value] in values
    log "Checking json path: \"#{jsonPath}\""
    if _.has json, jsonPath
      oriValue = _.get json, jsonPath
      log "  Replace value \"#{oriValue}\""
      log "           with \"#{value}\""
      _.set json, jsonPath, value
      log ""

    else
      log "[ERROR] path \"#{jsonPath}\" not found!"
      process.exit 1
  log ''
  log "Writing file to #{outfile}."
  fs.writeFileSync(outfile, yaml.dump(json))
  
program
  .option('--in <input-file>', 'Input yaml file path')
  .option('--out <output-file>', 'Output result file')
  .option('--values <json-string>', 'JSON String for set value')
  .parse(process.argv)

if require.main == module
  values = fetchSetValues(program.values)

  log 'Modify yaml file from: '
  log "  in: #{program.in}"
  log "  to: #{program.out}"
  log "  values:"
  log "\n#{yaml.dump values}"
  
  unless _.isArray values
    log "skip, Values is not Array."
    console.log values
    log '----'
    return process.exit 1

  try 
    modYaml program.in, program.out, values
    log 'Done.'
  catch e
    log 'Error!!!'
    log '--- message ----'
    log e
    console.log e
    log '----------------'
    process.exit(1)
