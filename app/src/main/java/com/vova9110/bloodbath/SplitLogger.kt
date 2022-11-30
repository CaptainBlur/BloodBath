package com.vova9110.bloodbath

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.*
import java.util.logging.Formatter
import java.util.regex.Pattern

class SplitLogger private constructor() {

    companion object {
        private var mainLogger: Logger? = null
        private var alarmLogger: Logger? = null
        private var extractLogs: (() -> Unit)? = null

        private val logcatHandler = object: Handler(){
            private val f = object: Formatter() {
                override fun format(record: LogRecord?): String {
                    val thrown: Throwable? = record!!.thrown
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
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

        private val fileFormatter = object: Formatter() {
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
        fun manageDirectory(parentDirectory: File, keepFiles: Int = 4, targetExtension: String = "null", deleteUnmatched: Boolean = false, sl: SLCompanion? = null): String{
            var status = parentDirectory.name
            if (!parentDirectory.exists()) parentDirectory.mkdir().also { status+= "\nNew directory created";sl?.fp(status); return status }

            val list = parentDirectory.listFiles()!!.toList().toMutableList()
            if (list.isEmpty()){status+= "\nDirectory is empty";sl?.fp(status); return status}

            val cleanseBuffer = Array<File?>(list.size) { null }
            var bC = 0
            if (deleteUnmatched) for (file in list) if (!Pattern.compile("$targetExtension$").matcher(file.name).find()){
                cleanseBuffer[bC] = file
                bC++
            }
            for (file in cleanseBuffer) if (file!=null) list.remove(file)

            if (list.size<=keepFiles){status+= "\nFiles count satisfies condition"; sl?.fp(status); return status}
            list.sortBy { element-> element.lastModified() }
            for (file in list.subList(0, list.size - keepFiles)){
                cleanseBuffer[bC] = file
                bC++
            }
            for (file in cleanseBuffer){
                if (file!=null) status += try {
                    file.delete()
                    "\nFile ${file.name} successfully deleted"
                } catch (e: IOException){
                    "\nCannot delete file ${file.name}"
                }
            }
            sl?.fp(status)
            return status
        }

        @JvmStatic
        fun initialize (context: Context, inAlarmUnit: Boolean = false){
            val slCompanion = SLCompanion(inAlarmUnit, "SplitLogger", true)

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
                    if (buffer.isNotEmpty()) slCompanion.f(buffer.toString())
                }
            }

            if (!inAlarmUnit && mainLogger==null){
                val parentDirectory = File(context.getExternalFilesDir(null), "main_unit_logs")
                mainLogger = Logger.getAnonymousLogger()
                mainLogger?.useParentHandlers= false
                mainLogger?.level = Level.ALL

                mainLogger?.addHandler(logcatHandler)
                //We need to clear directory from .lck files because both handlers can't be closed properly before exiting the application
                slCompanion.f("***Logcat handler initialized")
                manageDirectory(parentDirectory, 8, ".log", true, slCompanion)

                var timeName = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT, Locale.getDefault()).format(Calendar.getInstance().time)
                timeName = timeName.replace(Char(32), Char(95))
                timeName = timeName.replace(Char(47), Char(46))
                val ID = Random().nextInt(1000).toString()

                try {
                    val verboseHandler = FileHandler(parentDirectory.path + "/${timeName}_V_${ID}.log").apply { formatter = fileFormatter; level = Level.ALL }
                    val infoHandler = FileHandler(parentDirectory.path + "/${timeName}_I_${ID}.log").apply { formatter = fileFormatter; level = Level.INFO }
                    mainLogger?.addHandler(verboseHandler)
                    mainLogger?.addHandler(infoHandler)
                    slCompanion.f("***File handlers initialized")
                } catch (e: Exception) {
                    slCompanion.s("***Can't initialize file handlers, $e")
                }
                pullEncrypted()
            }
            else if (!inAlarmUnit) slCompanion.w("***Can't create 'main' logger when it already initialized")

            if (inAlarmUnit && alarmLogger==null){
                val childName = "alarm_unit_logs"
                val parentDirectory = File(context.getExternalFilesDir(null), childName)
                alarmLogger = Logger.getAnonymousLogger()
                alarmLogger?.useParentHandlers= false
                alarmLogger?.level = Level.ALL

                alarmLogger?.addHandler(logcatHandler)
                //We need to clear directory from .lck files because both handlers can't be closed properly before exiting the application
                slCompanion.f("***Logcat handler initialized")
                manageDirectory(parentDirectory, 8, ".log", true, slCompanion)

                var timeName = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT, Locale.getDefault()).format(Calendar.getInstance().time)
                timeName = timeName.replace(Char(32), Char(95))
                timeName = timeName.replace(Char(47), Char(46))
                val ID = Random().nextInt(1000).toString()

                try {
                    val verboseHandler = FileHandler(parentDirectory.path + "/${timeName}_V_${ID}.log").apply { formatter = fileFormatter; level = Level.ALL }
                    val infoHandler = FileHandler(parentDirectory.path + "/${timeName}_I_${ID}.log").apply { formatter = fileFormatter; level = Level.INFO }
                    alarmLogger?.addHandler(verboseHandler)
                    alarmLogger?.addHandler(infoHandler)
                    slCompanion.f("***File handlers initialized")
                } catch (e: Exception) {
                    slCompanion.s("***Can't initialize file handlers, trying encrypted storage")
                    try {
                        val stream = context.createDeviceProtectedStorageContext().openFileOutput("${ID}.tmp", Context.MODE_PRIVATE)
                        alarmLogger?.addHandler(encryptedHandler(stream))
                    } catch (e: FileNotFoundException){
                        slCompanion.f("***Can't create no file\n${e.message}")
                    }
                }
                pullEncrypted()
            }
            else if (inAlarmUnit) slCompanion.w("***Can't create 'alarm' logger when it already initialized")
        }
    }

    class SLCompanion private constructor (){
        private var affiliation = 0
        private var TAG: String = ""

        constructor (className: String, useRawName: Boolean = false): this (){
            affiliation = if (alarmLogger!=null) 2 else if (mainLogger!=null) 1 else 0

            if (useRawName){ TAG = "TAG_$className"; return}

            val first = Pattern.compile("[A-Z][a-z]*").matcher(className.substring(className.lastIndexOf(Char(46))))

            var result = ""
            while (first.find())
                result += when(first.group()){
                    "Main"-> "M:"
                    "Alarm"-> "A:"
                    "Activity"-> "A"
                    "Handler"-> "H"
                    else-> first.group()
                }
            val second = Pattern.compile("[A-Z]:?[a-z]{0,2}[^A-Z^equoaijy]?").matcher(result)

            result = "TAG_"
            while (second.find()) result+=second.group()

            TAG = if (result.length <= 23) result else result.substring(0, 23)
        }

        constructor (affiliation: Boolean, className: String, useRawName: Boolean = false): this (className, useRawName){
            this.affiliation = if (!affiliation) 1 else 2
        }


        private fun printMsg(
            msg: String, logLevel: Level,
            methodPrint: Boolean = false,
            tr: Throwable? = null,
        ){

            val methodName = if (methodPrint) Thread.currentThread().stackTrace[5].methodName + ": " else ""
            try {
                if (affiliation==1 || affiliation==3) mainLogger!!.logp(logLevel, TAG, methodName, msg, tr)
                if (affiliation==2 || affiliation==3) alarmLogger!!.logp(logLevel, TAG, methodName, msg, tr)
                if (affiliation==0) Log.e(TAG, "***Requested logger ($affiliation) not initialized yet***")
            }
            catch (e: NullPointerException){
                Log.e(TAG, "printMsg", e)
            }
        }


        fun s(msg: String) = printMsg(msg, Level.SEVERE)
        fun sp(msg: String) = printMsg(msg, Level.SEVERE, true)
        fun s(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE, tr = tr)
        fun sp(msg: String, tr: Throwable? = null) = printMsg(msg, Level.SEVERE,true, tr)

        fun w(msg: String) = printMsg(msg, Level.WARNING)
        fun wp(msg: String) = printMsg(msg, Level.WARNING, true)

        fun i(msg: String) = printMsg(msg, Level.INFO)
        fun ip(msg: String) = printMsg(msg, Level.INFO, true)

        fun f(msg: String) = printMsg(msg, Level.FINE)
        fun fp(msg: String) = printMsg(msg, Level.FINE, true)

        fun fr(msg: String) = printMsg(msg, Level.FINER)
        fun frp(msg: String) = printMsg(msg, Level.FINER, true)

        fun fst(msg: String) = printMsg(msg, Level.FINEST)
        fun fstp(msg: String) = printMsg(msg, Level.FINEST, true)
    }
}
