/*
 * Copyright (c) 2012, the authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.script

import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskProcessor
import nextflow.util.CacheHelper

/**
 * Any user defined script will extends this class, it provides the base execution context
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
abstract class AbstractScript extends Script {


    protected AbstractScript(){ }

    protected AbstractScript(Binding binding) {
        super(binding)
    }

    /*
     * The script execution session, declare it private to prevent the user script to be able to access it
     */
    @Lazy
    private Session session = { getBinding()?.getVariable('__$session') as Session } ()

    private final random = new Random()


    /**
     * Holds the configuration object which will used to execution the user tasks
     */
    @Lazy
    Map config = { session.config } ()

    @Lazy
    InputStream stdin = { System.in }()

    /*
     * The last produced result object
     */
    private result

    def getResult() { result }

    /*
     * The last created task processor
     */
    private TaskProcessor taskProcessor

    def TaskProcessor getTaskProcessor() { taskProcessor }

    /**
     * Enable disable task 'echo' configuration property
     * @param value
     */
    def void echo(boolean value = true) {
        config.task.echo = value
    }

    /**
     * Stop the current execution returning an error code and message
     *
     * @param exitCode The exit code to be returned
     * @param message The message that will be reported in the log file (optional)
     */
    def void exit(int exitCode, String message = null) {
        if ( exitCode && message ) {
            log.error message
        }
        else if ( message ) {
            log.info message
        }
        System.exit(exitCode)
    }

    /**
     * Stop the current execution returning a 0 error code and the specified message
     *
     * @param message The message that will be reported in the log file
     */
    def void exit( String message ) {
        exit(0, message)
    }

    /**
     * Create a folder for the given key. It guarantees to return the same folder name
     * the same provided object key.
     *
     * @param key An object to be used as cache-key creating the folder, it can be any object
     *          or an array or objects to use multi-objects key
     *
     * @return The {@code File} to the cached directory or a newly created folder foe the specified key
     */
    File cacheableDir( Object key ) {
        assert key, "Please specify the 'key' argument on 'cacheableDir' method"

        def hash = CacheHelper.hasher([ session.uniqueId, key, session.cacheable ? 0 : random.nextInt() ]).hash()

        def file = CacheHelper.folderForHash(hash)
        if( !file.exists() && !file.mkdirs() ) {
            throw new IOException("Unable to create folder: $file -- Check file system permission" )
        }

        return file
    }

    /**
     * Create a file for the given key. It guarantees to return the same file name
     * the same provided object key.
     *
     * @param key
     * @param name
     * @return
     */
    File cacheableFile( Object key, String name = null ) {

        // the cacheability is guaranteed by the folder
        def folder = cacheableDir(key)

        if( !name ) {
            name = key instanceof File ? key.name : key.toString()
        }

        return new File(folder, name)
    }

    /**
     * Create a task processor
     *
     * @param name The name used to label this processor
     * @param block The code block to be executed
     * @return The {@code Processor} instance
     */
    def createProcessor( String name, boolean bindOnTermination, Closure<String> block  ) {

        assert block

        // create the processor object
        def processor = session.createProcessor(this, bindOnTermination)

        // set the name, when specified
        if( name ) { processor.name(name) }

        // invoke the code block, which will return the script closure to the executed
        def script = processor.with ( block ) as Closure
        if ( !script ) throw new IllegalArgumentException("Missing script in the specified task block -- make sure it terminates with the script string to be executed")

        // set the script and run !
        processor.script(script)

        // keep track of the last create processor
        return taskProcessor = processor

    }


    /**
     * Creates and runs a task
     *
     * @param name The name used to label this processor
     * @param block The code block to be executed
     * @return The task result as returned by {@code Processor#run}
     */
    def task( String name = null, Closure<String> block ) {

        result = createProcessor(name,false,block).run()

    }


    def merge( String name = null, Closure<String> block ) {

        result = createProcessor(name,true,block) .run()

    }



}
