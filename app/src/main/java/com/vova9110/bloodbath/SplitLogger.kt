package com.vova9110.bloodbath

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
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

        private val logcatHandler = object: Handler(){
            private val f = object: Formatter() {
                override fun format(record: LogRecord?): String {
                    val thrown: Throwable? = record!!.thrown
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
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

            override fun flush() {}

            override fun close() {}

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
                if (record.sourceMethodName!=null) sw.write(record.sourceMethodName)
                sw.write(record.message)
                sw.write("\n")
                if (thrown != null) thrown.printStackTrace(pw)
                pw.flush()
                return sw.toString()
            }
        }

        @JvmStatic
        fun manageDirectory(parentDirectory: File, keepFiles: Int = 4, targetExtension: String = "null", deleteUnmatched: Boolean = false, sl: SLCompanion? = null): String{
            var status = parentDirectory.name
            if (!parentDirectory.exists()) parentDirectory.mkdir().also { status+= "\nNew directory created"; return status }

            val list = parentDirectory.listFiles()!!.toList().toMutableList()
            if (list.isEmpty()){status+= "\nDirectory is empty"; return status}

            val cleanseBuffer = Array<File?>(list.size) { null }
            var bC = 0
            if (deleteUnmatched) for (file in list) if (!Pattern.compile("$targetExtension$").matcher(file.name).find()){
                cleanseBuffer[bC] = file
                bC++
            }
            for (file in cleanseBuffer) if (file!=null) list.remove(file)

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
            val slCompanion = SLCompanion(false, "SplitLogger", true)

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

                val verboseHandler = FileHandler(parentDirectory.path + "/${timeName}_V_${ID}.log").apply { formatter = fileFormatter; level = Level.ALL }
                val infoHandler = FileHandler(parentDirectory.path + "/${timeName}_I_${ID}.log").apply { formatter = fileFormatter; level = Level.INFO }
                mainLogger?.addHandler(verboseHandler)
                mainLogger?.addHandler(infoHandler)
                slCompanion.f("***File handlers initialized")
            }
            else if (!inAlarmUnit) slCompanion.s("***Can't create 'main' logger when it already initialized")

            if (inAlarmUnit && alarmLogger==null){
                val parentDirectory = File(context.getExternalFilesDir(null), "alarm_unit_logs")
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

                val verboseHandler = FileHandler(parentDirectory.path + "/${timeName}_V_${ID}.log").apply { formatter = fileFormatter; level = Level.ALL }
                val infoHandler = FileHandler(parentDirectory.path + "/${timeName}_I_${ID}.log").apply { formatter = fileFormatter; level = Level.INFO }
                alarmLogger?.addHandler(verboseHandler)
                alarmLogger?.addHandler(infoHandler)
                slCompanion.f("***File handlers initialized")
            }
            else if (inAlarmUnit) slCompanion.s("***Can't create 'alarm' logger when it already initialized")
        }
    }

    class SLCompanion private constructor (){
        private var affiliation: Boolean = false
        private var TAG: String = ""

        constructor (affiliation: Boolean, className: String, useRawName: Boolean = false): this (){
            this.affiliation = affiliation
            if (useRawName){ TAG = "TAG_$className"; return}

            val first = Pattern.compile("[A-Z][a-z]*").matcher(className)

            var result = ""
            while (first.find())
                result += when(first.group()){
                    "Main"-> "M:"
                    "Alarm"-> "A:"
                    "Activity"-> "A"
                    else-> first.group()
                }
            val second = Pattern.compile("[A-Z]:?[a-z]{0,2}[^A-Z^equoaijy]?").matcher(result)

            result = "TAG_"
            while (second.find()) result+=second.group()

            TAG = if (result.length <= 23) result else result.substring(0, 23)
        }


        private fun printMsg(
            msg: String,
            logger: Boolean, logLevel: Level,
            methodPrint: Boolean = false,
            tr: Throwable? = null,
        ){

            val methodName = if (methodPrint) Thread.currentThread().stackTrace[5].methodName + ": " else ""
            try {
                if (!affiliation) mainLogger!!.logp(logLevel, TAG, methodName, msg, tr)
                else alarmLogger!!.logp(logLevel, TAG, methodName, msg, tr)
            }
            catch (e: NullPointerException){
                Log.e(TAG, "***Requested logger (${if (!logger) "main" else "alarm"}) not initialized yet***")
            }
        }


        fun s(msg: String) = printMsg(msg, affiliation, Level.SEVERE)
        fun sp(msg: String) = printMsg(msg, affiliation, Level.SEVERE, true)
        fun s(msg: String, tr: Throwable? = null) = printMsg(msg, affiliation, Level.SEVERE, tr = tr)
        fun sp(msg: String, tr: Throwable? = null) = printMsg(msg, affiliation, Level.SEVERE,true, tr)

        fun w(msg: String) = printMsg(msg, affiliation, Level.WARNING)
        fun wp(msg: String) = printMsg(msg, affiliation, Level.WARNING, true)

        fun i(msg: String) = printMsg(msg, affiliation, Level.INFO)
        fun ip(msg: String) = printMsg(msg, affiliation, Level.INFO, true)

        fun f(msg: String) = printMsg(msg, affiliation, Level.FINE)
        fun fp(msg: String) = printMsg(msg, affiliation, Level.FINE, true)

        fun fr(msg: String) = printMsg(msg, affiliation, Level.FINER)
        fun frp(msg: String) = printMsg(msg, affiliation, Level.FINER, true)

        fun fst(msg: String) = printMsg(msg, affiliation, Level.FINEST)
        fun fstp(msg: String) = printMsg(msg, affiliation, Level.FINEST, true)
    }
}
