package com.vova9110.bloodbath

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.*
import java.util.logging.Formatter
import java.util.regex.Pattern

open class SplitLogger {

    companion object {
        private val mainLogger: Logger = Logger.getAnonymousLogger()
        @JvmStatic
        protected var initialized: Boolean = false
        private const val TAG = "SplitLogger"

        @JvmStatic
        protected val logcatHandler = object: Handler(){
            private val f = object: Formatter() {
                override fun format(record: LogRecord?): String {
                    val thrown: Throwable? = record!!.thrown
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    if (record.level==Level.FINE) sw.write("\t")
                    if (record.level==Level.FINER) sw.write("\t\t")
                    if (record.level==Level.FINEST) sw.write("\t\t\t")
                    if (record.sourceMethodName!=null) sw.write(record.sourceMethodName)

                    sw.write(record.message)
                    sw.write("\n")
                    if (thrown != null) thrown.printStackTrace(pw)
                    pw.flush()
                    return sw.toString()
                }
            }
            init {
                formatter = f
                level = Level.ALL
            }

            private fun getAndroidLevel(level: Level): Int {
                val value = level.intValue()
                return if (value >= 1000) { // SEVERE
                    Log.ERROR
                } else if (value >= 900) { // WARNING
                    Log.WARN
                } else if (value >= 800) { // INFO
                    Log.INFO
                } else {
                    Log.DEBUG
                }
            }

            override fun publish(record: LogRecord?) {
                val level = getAndroidLevel(record!!.level)
                val tag = record.sourceClassName

                if (record.level.intValue() < this.level.intValue()) return

                try {
                    val message = formatter.format(record)
                    Log.println(level, tag, message)
                } catch (e: RuntimeException) {
                    Log.e("LogcatHandler", "Error logging message.", e)
                }
            }

            override fun flush() = Unit

            override fun close() = Unit

        }

        init {
            mainLogger.useParentHandlers= false
            mainLogger.level = Level.ALL

            mainLogger.addHandler(logcatHandler)
        }

        @JvmStatic
        protected val fileFormatter = object: Formatter() {
            override fun format(record: LogRecord?): String {
                val thrown: Throwable? = record!!.thrown
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                val date = Date(record.millis)
                val maxLength = 12+2+7+2+23

                sw.write(SimpleDateFormat("HH:mm:ss:SSS__", Locale.getDefault()).format(date))
                sw.write(record.level.name)
                sw.write(CharArray(Level.WARNING.name.length - record.level.name.length + 2) { Char(95)})
                sw.write(record.sourceClassName)
                sw.write(CharArray(maxLength - sw.buffer.length + 2) { Char(32)})

                sw.write("|")
                if (record.level==Level.FINER) sw.write("\t")
                if (record.level==Level.FINEST) sw.write("\t\t")
                if (record.sourceMethodName!=null) sw.write(record.sourceMethodName)
                sw.write(record.message)
                sw.write("\n")
                if (thrown != null) thrown.printStackTrace(pw)
                pw.flush()
                return sw.toString()
            }
        }

        private class encryptedHandler(stream: FileOutputStream): Handler(){
            val writer: BufferedWriter
            init {
                writer = BufferedWriter(OutputStreamWriter(stream))
                formatter = fileFormatter
            }
            override fun publish(record: LogRecord?) = writer.write(formatter.format(record)).also { writer.flush() }

            override fun flush() = Unit

            override fun close() = writer.close()

        }
        

        @JvmStatic
        fun manageDirectory(
            parentDirectory: File,
            putInFolders: Boolean = false,
            keepEntities: Short = 4,
            targetExtension: String = "null",
            deleteUnmatched: Boolean = false
        ): String{
            var status = parentDirectory.name
            if (!parentDirectory.exists()) parentDirectory.mkdir().also { status+= "\nNew directory created"; return status }

            val list = parentDirectory.listFiles()!!.toList().toMutableList()
            if (list.isEmpty()){status+= "\nDirectory is empty"; return status}

            val cleanseBuffer = Array<File?>(list.size) { null }
            var bC = 0 //cleanse buffer count

            if (deleteUnmatched && !putInFolders) {
                for (file in list) if (!Pattern.compile("$targetExtension$").matcher(file.name).find()) {
                    cleanseBuffer[bC] = file
                    bC++
                }
            }
            else if (deleteUnmatched) for (file in list) if (!file.isDirectory) { //don't mind target extension, we're handling directories now
                cleanseBuffer[bC] = file
                bC++
            }
            for (file in cleanseBuffer) if (file!=null) list.remove(file)

            if (list.size <= keepEntities) status+= "\nFiles count satisfies condition"
            else {
                list.sortBy { element -> element.lastModified() }
                for (file in list.subList(0, list.size - keepEntities)) {
                    cleanseBuffer[bC] = file
                    bC++
                }
            }

            for (file in cleanseBuffer){
                if (file!=null) status += try {
                    file.deleteRecursively()
                    "\nFile ${file.name} successfully deleted"
                } catch (e: IOException){
                    "\nCannot delete file ${file.name}"
                }
            }
            return status
        }


        @JvmStatic
        protected lateinit var currentDir: File
        @JvmStatic
        fun initialize (context: Context){

            fun pullEncrypted() {
                val encContext = context.createDeviceProtectedStorageContext()
                val filesList = encContext.filesDir.listFiles { file -> file.length() != 0L }
                val buffer = StringBuffer()

                filesList?.let {
                    for (file in it) {
                        buffer.append("***Pulling data from file ${file.name}***\n")
                        for (line in Files.readAllLines(file.toPath())) buffer.append(line + "\n")
                        buffer.append("***End of file***")

                        Files.delete(file.toPath())
                    }
                    if (buffer.isNotEmpty()) Log.d("TAG",buffer.toString())
                }
            }

            if (!initialized){
                val parentDirectory = File(context.getExternalFilesDir(null), "main_unit_logs")
                mainLogger.logp(Level.FINE, TAG, "manageDirectory: ", manageDirectory(
                    parentDirectory,
                    true,
                    keepEntities = 12,
                    targetExtension = ".log",
                    deleteUnmatched = true
                ))

                val timeName = SimpleDateFormat("MM.dd.yy_HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis()))
                val ID = Random().nextInt(1000).toString()
                val dedicatedDirectory = File(parentDirectory, "${timeName}_${ID}").apply { if (!mkdir()) mainLogger.logp(Level.SEVERE, TAG, "initializer", "failed to create new dir")}
                currentDir = dedicatedDirectory

                try {
                    val verboseHandler = FileHandler(dedicatedDirectory.path + "/Verbose.log").apply { formatter = fileFormatter; level = Level.ALL }
                    val infoHandler = FileHandler(dedicatedDirectory.path + "/Info.log").apply { formatter = fileFormatter; level = Level.INFO }
                    mainLogger.addHandler(verboseHandler)
                    mainLogger.addHandler(infoHandler)
                    mainLogger.logp(Level.FINE, TAG, null, "***File handlers initialized")

                    pullEncrypted()
                } catch (e: Exception) {
                    mainLogger.logp(Level.SEVERE, TAG, null,"***Can't initialize file handlers, trying encrypted storage")
                    try {
                        val stream = context.createDeviceProtectedStorageContext().openFileOutput("${ID}.tmp", Context.MODE_PRIVATE)
                        mainLogger.addHandler(encryptedHandler(stream))
                    } catch (e: FileNotFoundException){
                        mainLogger.logp(Level.SEVERE, TAG, null,"***Can't create output file\n${e.message}")
                    }
                }
                initialized = true
            }
            else mainLogger.logp(Level.WARNING, TAG, null,"***Logger already initialized")
        }

        /*
        From there, the following code is actual log records handling
         */
        @JvmStatic
        protected val tags = HashMap<String, String>()
        private fun getTag(rawClassName: String): Array<String>{
            //Extracting essentials from raw string
            var className = rawClassName.substringAfterLast(Char(0x2e))
            if (Pattern.compile(".*$+").matcher(className).find())
                className = className.substringBefore(Char(0x24))

            //Trying to find a match in HashMap
            if (tags.containsKey(className)) return Array(2) { i ->
                if (i == 0) tags.getValue(className) else className
            }

            val first = Pattern.compile("[A-Z][a-z]*").matcher(className)

            //Basically, we're one-by-one checking words starting from capital letter, and replacing them with switch-case
            var result = String()
            while (first.find())
                result += when(first.group()){
                    "Main"-> "M:"
                    "Alarm","Alarms"-> "A:"
                    "Activity"-> "A"
                    "Handler"-> "H"
                    else-> first.group()
                }
            //Here's the main pattern defines general look of the resulting tagname
            val second = Pattern.compile("[A-Z]:?[a-z]{0,3}[^A-Z^equoaijy]?").matcher(result)

            result = "TAG_"
            while (second.find()) result+=second.group()

            val tag = if (result.length <= 23) result else result.substring(0, 23)

            tags[className] = tag
            return Array(2) { i ->
                if (i == 0) tag else className
            }
        }

        inline fun <reified T : Any>T.printObject(): String{
            return when (this){
                is Array<*>->
                    Gson().toJson(this)
                is ByteArray-> Gson().toJson(this.toTypedArray())
                is CharArray-> Gson().toJson(this.toTypedArray())
                is ShortArray-> Gson().toJson(this.toTypedArray())
                is IntArray-> Gson().toJson(this.toTypedArray())
                is LongArray-> Gson().toJson(this.toTypedArray())
                is FloatArray-> Gson().toJson(this.toTypedArray())
                is DoubleArray-> Gson().toJson(this.toTypedArray())
                is BooleanArray-> Gson().toJson(this.toTypedArray())

                else-> this.toString()
            }
        }
        private fun findTrace(trace: Array<StackTraceElement>): StackTraceElement{
            var i = 0
            val pattern = Pattern.compile("SplitLogger")
            for (k in trace.indices){
                if (pattern.matcher(trace[k].toString()).find()) i = k
            }
            return trace[i+1]
        }
        private fun printMsg(
            msg: String, logLevel: Level,
            methodPrint: Boolean = false,
            //Using offset for method name getter in case if caller object is gotten from some container like Dagger or System itself
            callable: Boolean = false,
            tr: Throwable? = null,
        ){
            val offset = if (callable) 1 else 0
            val trace = findTrace(Thread.currentThread().stackTrace)
            val methodName = if (methodPrint) trace.methodName.substringAfterLast(Char(0x24)) + ": " else ""
            //In case if we have caller object in Dagger container

            val tag = getTag(trace.className)[0]

            if (!initialized) mainLogger.logp(Level.WARNING, tag, null,"***Logger working without file output")
            mainLogger.logp(logLevel, tag, methodName, msg, tr)
//            for (trace in thread.stackTrace) mainLogger.logp(Level.INFO, tag, "", trace.toString())

        }
        private fun printPass(
            msg: String
        ){
            val thread = findTrace(Thread.currentThread().stackTrace)

            val tag = getTag(thread.className)[0]
            val noTag = tag.substringAfter(Char(0x5f))

            if (!initialized) mainLogger.logp(Level.WARNING, tag, null,"***Logger working without file output")
            mainLogger.logp(Level.FINE, tag, "", "\ufe4f$noTag $msg")
        }

        @JvmStatic
        fun s(msg: String) = printMsg(msg, Level.SEVERE)
        @JvmStatic
        fun s(obj: Any) = printMsg(obj.printObject(), Level.SEVERE)
        @JvmStatic
        fun sp(msg: String) = printMsg(msg, Level.SEVERE, true)
        @JvmStatic
        fun sp(obj: Any) = printMsg(obj.printObject(), Level.SEVERE, true)
        @JvmStatic
        fun spc(msg: String) = printMsg(msg, Level.SEVERE, true, callable = true)
        @JvmStatic
        fun spc(obj: Any) = printMsg(obj.printObject(), Level.SEVERE, true, callable = true)
        @JvmStatic
        fun s(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE, tr = tr)
        @JvmStatic
        fun s(obj: Any, tr: Throwable? = null) = printMsg(obj.printObject(), Level.SEVERE, tr = tr)
        @JvmStatic
        fun sp(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE,true, tr = tr)
        @JvmStatic
        fun sp(obj: Any, tr: Throwable? = null) = printMsg(obj.printObject(), Level.SEVERE,true, tr = tr)
        @JvmStatic
        fun spc(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE,true, true, tr)
        @JvmStatic
        fun spc(obj: Any, tr: Throwable? = null) = printMsg(obj.printObject(), Level.SEVERE,true, true, tr)

        @JvmStatic
        fun w(msg: String) = printMsg(msg, Level.WARNING)
        @JvmStatic
        fun w(obj: Any) = printMsg(obj.printObject(), Level.WARNING)
        @JvmStatic
        fun wp(msg: String) = printMsg(msg, Level.WARNING, true)
        @JvmStatic
        fun wp(obj: Any) = printMsg(obj.printObject(), Level.WARNING, true)
        @JvmStatic
        fun wpc(msg: String) = printMsg(msg, Level.WARNING, true, true)
        @JvmStatic
        fun wpc(obj: Any) = printMsg(obj.printObject(), Level.WARNING, true, true)

        @JvmStatic
        fun i(msg: String) = printMsg(msg, Level.INFO)
        @JvmStatic
        fun i(obj: Any) = printMsg(obj.printObject(), Level.INFO)
        @JvmStatic
        fun ip(msg: String) = printMsg(msg, Level.INFO, true)
        @JvmStatic
        fun ip(obj: Any) = printMsg(obj.printObject(), Level.INFO, true)
        @JvmStatic
        fun ipc(msg: String) = printMsg(msg, Level.INFO, true, true)
        @JvmStatic
        fun ipc(obj: Any) = printMsg(obj.printObject(), Level.INFO, true, true)

        @JvmStatic
        fun f(msg: String) = printMsg(msg, Level.FINE)
        @JvmStatic
        fun f(obj: Any) = printMsg(obj.printObject(), Level.FINE)
        @JvmStatic
        fun fp(msg: String) = printMsg(msg, Level.FINE, true)
        @JvmStatic
        fun fp(obj: Any) = printMsg(obj.printObject(), Level.FINE, true)
        @JvmStatic
        fun fpc(msg: String) = printMsg(msg, Level.FINE, true, true)
        @JvmStatic
        fun fpc(obj: Any) = printMsg(obj.printObject(), Level.FINE, true, true)

        @JvmStatic
        fun fr(msg: String) = printMsg(msg, Level.FINER)
        @JvmStatic
        fun fr(obj: Any) = printMsg(obj.printObject(), Level.FINER)
        @JvmStatic
        fun frp(msg: String) = printMsg(msg, Level.FINER, true)
        @JvmStatic
        fun frp(obj: Any) = printMsg(obj.printObject(), Level.FINER, true)
        @JvmStatic
        fun frpc(msg: String) = printMsg(msg, Level.FINER, true, true)
        @JvmStatic
        fun frpc(obj: Any) = printMsg(obj.printObject(), Level.FINER, true, true)

        @JvmStatic
        fun fst(msg: String) = printMsg(msg, Level.FINEST)
        @JvmStatic
        fun fst(obj: Any) = printMsg(obj.printObject(), Level.FINEST)
        @JvmStatic
        fun fstp(msg: String) = printMsg(msg, Level.FINEST, true)
        @JvmStatic
        fun fstp(obj: Any) = printMsg(obj.printObject(), Level.FINEST, true)
        @JvmStatic
        fun fstpc(msg: String) = printMsg(msg, Level.FINEST, true, true)
        @JvmStatic
        fun fstpc(obj: Any) = printMsg(obj.printObject(), Level.FINEST, true, true)

        @JvmStatic
        fun en() = printPass("<--")
        @JvmStatic
        fun ex() = printPass("-->")
    }

}




class SplitLoggerUI : SplitLogger(){
    companion object UILogger {
        private const val TAG = "SplitLoggerUI"
        private val uiLogger = Logger.getAnonymousLogger()

        init{
            uiLogger.useParentHandlers = false
            uiLogger.level = Level.ALL

            uiLogger.addHandler(logcatHandler)
        }
        fun initialize (context: Context){
            //Checking parent logger
            try {
                assert(initialized)
            } catch (e: Exception){ Log.e(TAG, "Not initialized", e.cause) }

            try {
                val verboseHandler = FileHandler(currentDir.path + "/UI.log").apply { formatter = fileFormatter; level = Level.ALL }
                uiLogger.addHandler(verboseHandler)
                uiLogger.logp(Level.FINE, TAG, null, "***File handlers initialized")
            } catch (e: Exception) {
                uiLogger.logp(Level.SEVERE, TAG, null,"***Can't initialize file handlers, trying encrypted storage")
            }
        }


        private fun getTag(rawClassName: String): Array<String>{
            //Extracting essentials from raw string
            var className = rawClassName.substringAfterLast(Char(0x2e))
            if (Pattern.compile(".*$+").matcher(className).find())
                className = className.substringBefore(Char(0x24))

            //Trying to find a match in HashMap
            if (SplitLogger.tags.containsKey(className)) return Array(2) { i ->
                if (i == 0) SplitLogger.tags.getValue(className) else className
            }

            val first = Pattern.compile("[A-Z][a-z]*").matcher(className)

            var result = String()
            while (first.find())
                result += first.group()

            //Here's the main pattern defines general look of the resulting tagName
            val second = Pattern.compile("[A-Z]").matcher(result)

            result = "TAG-UI_"
            while (second.find()) result+=second.group()

            val tag = if (result.length <= 20) result else result.substring(0, 20)

            tags[className] = tag
            return Array(2) { i ->
                if (i == 0) tag else className
            }
        }

        inline fun <reified T : Any>T.printObject(): String{
            return when (this){
                is Array<*>->
                    Gson().toJson(this)
                is ByteArray-> Gson().toJson(this.toTypedArray())
                is CharArray-> Gson().toJson(this.toTypedArray())
                is ShortArray-> Gson().toJson(this.toTypedArray())
                is IntArray-> Gson().toJson(this.toTypedArray())
                is LongArray-> Gson().toJson(this.toTypedArray())
                is FloatArray-> Gson().toJson(this.toTypedArray())
                is DoubleArray-> Gson().toJson(this.toTypedArray())
                is BooleanArray-> Gson().toJson(this.toTypedArray())

                else-> this.toString()
            }
        }
        private fun findTrace(trace: Array<StackTraceElement>): StackTraceElement{
            var i = 0
            val pattern = Pattern.compile("SplitLoggerUI")
            for (k in trace.indices){
                if (pattern.matcher(trace[k].toString()).find()) i = k
            }
            return trace[i+1]
        }
        private fun printMsg(
            msg: String, logLevel: Level,
            methodPrint: Boolean = false,
            //Using offset for method name getter in case if caller object is gotten from some container like Dagger or System itself
            callable: Boolean = false,
            tr: Throwable? = null,
        ){
            val offset = if (callable) 1 else 0
            val trace = findTrace(Thread.currentThread().stackTrace)
            val methodName = if (methodPrint) trace.methodName.substringAfterLast(Char(0x24)) + ": " else ""
            //In case if we have caller object in Dagger container

            val tag = getTag(trace.className)[0]

            if (!initialized) uiLogger.logp(Level.WARNING, tag, null,"***Logger working without file output")
            uiLogger.logp(logLevel, tag, methodName, msg, tr)
//            for (trace in thread.stackTrace) mainLogger.logp(Level.INFO, tag, "", trace.toString())

        }
        private fun printPass(
            msg: String
        ){
            val thread = findTrace(Thread.currentThread().stackTrace)

            val tag = getTag(thread.className)[0]
            val noTag = tag.substringAfterLast(Char(0x5f))

            if (!initialized) uiLogger.logp(Level.WARNING, tag, null,"***Logger working without file output")
            uiLogger.logp(Level.FINE, tag, "", "\ufe4f$noTag $msg")
        }

        @JvmStatic
        fun s(msg: String) = printMsg(msg, Level.SEVERE)
        @JvmStatic
        fun s(obj: Any) = printMsg(obj.printObject(), Level.SEVERE)
        @JvmStatic
        fun sp(msg: String) = printMsg(msg, Level.SEVERE, true)
        @JvmStatic
        fun sp(obj: Any) = printMsg(obj.printObject(), Level.SEVERE, true)
        @JvmStatic
        fun spc(msg: String) = printMsg(msg, Level.SEVERE, true, callable = true)
        @JvmStatic
        fun spc(obj: Any) = printMsg(obj.printObject(), Level.SEVERE, true, callable = true)
        @JvmStatic
        fun s(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE, tr = tr)
        @JvmStatic
        fun s(obj: Any, tr: Throwable? = null) = printMsg(obj.printObject(), Level.SEVERE, tr = tr)
        @JvmStatic
        fun sp(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE,true, tr = tr)
        @JvmStatic
        fun sp(obj: Any, tr: Throwable? = null) = printMsg(obj.printObject(), Level.SEVERE,true, tr = tr)
        @JvmStatic
        fun spc(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE,true, true, tr)
        @JvmStatic
        fun spc(obj: Any, tr: Throwable? = null) = printMsg(obj.printObject(), Level.SEVERE,true, true, tr)

        @JvmStatic
        fun w(msg: String) = printMsg(msg, Level.WARNING)
        @JvmStatic
        fun w(obj: Any) = printMsg(obj.printObject(), Level.WARNING)
        @JvmStatic
        fun wp(msg: String) = printMsg(msg, Level.WARNING, true)
        @JvmStatic
        fun wp(obj: Any) = printMsg(obj.printObject(), Level.WARNING, true)
        @JvmStatic
        fun wpc(msg: String) = printMsg(msg, Level.WARNING, true, true)
        @JvmStatic
        fun wpc(obj: Any) = printMsg(obj.printObject(), Level.WARNING, true, true)

        @JvmStatic
        fun i(msg: String) = printMsg(msg, Level.INFO)
        @JvmStatic
        fun i(obj: Any) = printMsg(obj.printObject(), Level.INFO)
        @JvmStatic
        fun ip(msg: String) = printMsg(msg, Level.INFO, true)
        @JvmStatic
        fun ip(obj: Any) = printMsg(obj.printObject(), Level.INFO, true)
        @JvmStatic
        fun ipc(msg: String) = printMsg(msg, Level.INFO, true, true)
        @JvmStatic
        fun ipc(obj: Any) = printMsg(obj.printObject(), Level.INFO, true, true)

        @JvmStatic
        fun f(msg: String) = printMsg(msg, Level.FINE)
        @JvmStatic
        fun f(obj: Any) = printMsg(obj.printObject(), Level.FINE)
        @JvmStatic
        fun fp(msg: String) = printMsg(msg, Level.FINE, true)
        @JvmStatic
        fun fp(obj: Any) = printMsg(obj.printObject(), Level.FINE, true)
        @JvmStatic
        fun fpc(msg: String) = printMsg(msg, Level.FINE, true, true)
        @JvmStatic
        fun fpc(obj: Any) = printMsg(obj.printObject(), Level.FINE, true, true)

        @JvmStatic
        fun fr(msg: String) = printMsg(msg, Level.FINER)
        @JvmStatic
        fun fr(obj: Any) = printMsg(obj.printObject(), Level.FINER)
        @JvmStatic
        fun frp(msg: String) = printMsg(msg, Level.FINER, true)
        @JvmStatic
        fun frp(obj: Any) = printMsg(obj.printObject(), Level.FINER, true)
        @JvmStatic
        fun frpc(msg: String) = printMsg(msg, Level.FINER, true, true)
        @JvmStatic
        fun frpc(obj: Any) = printMsg(obj.printObject(), Level.FINER, true, true)

        @JvmStatic
        fun fst(msg: String) = printMsg(msg, Level.FINEST)
        @JvmStatic
        fun fst(obj: Any) = printMsg(obj.printObject(), Level.FINEST)
        @JvmStatic
        fun fstp(msg: String) = printMsg(msg, Level.FINEST, true)
        @JvmStatic
        fun fstp(obj: Any) = printMsg(obj.printObject(), Level.FINEST, true)
        @JvmStatic
        fun fstpc(msg: String) = printMsg(msg, Level.FINEST, true, true)
        @JvmStatic
        fun fstpc(obj: Any) = printMsg(obj.printObject(), Level.FINEST, true, true)

        @JvmStatic
        fun en() = printPass("<--")
        @JvmStatic
        fun ex() = printPass("-->")
    }
}