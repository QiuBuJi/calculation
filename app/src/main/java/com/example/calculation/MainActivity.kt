package com.example.calculation

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.review.Util.SpanUtil
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList

private val regex = "《\\S+》".toRegex()

class MainActivity : AppCompatActivity() {
    private val pathExternal: File = Environment.getExternalStorageDirectory()
    private val pathFile = File(pathExternal, "Calculation/calculation.txt")
    lateinit var edit: SharedPreferences.Editor
    val TAG = "msg_mine"

    companion object {
        private var isAuto: Boolean = false
        private var isSave: Boolean = false
        private var hasMinuend: Boolean = false
        lateinit var tvResult: TextView
        val data = ArrayList<NameValue>()

        /**计算总额*/
        private fun calculation() {
            //所有数值总计
            var amount = 0f
            for (datum in data) amount += datum.value
            //精确到小数点后2位
            val strAmount = String.format("%.2f", amount)

            //显示格式化的字符串
            SpanUtil.create()
                .addForeColorSection("总额：", Color.GRAY)
                .addRelSizeSection(strAmount, 2f).setForeColor(strAmount, Color.BLACK)
                .addForeColorSection("元", Color.GRAY)
                .showIn(tvResult)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvResult = main_tvResult

        //自动计算&手动计算历史状态恢复
        val sp = getSharedPreferences("setting", 0)
        edit = sp.edit()
        isAuto = sp.getBoolean("autoMode", false)
        main_btCaculation.text = if (isAuto) "自动计算" else "计算"

        //权限检查
        requestPermission()
        //加载数据
        loadDataTo(data)

        //初始化列表
        val adapter = Adapter(this, data)
        main_rvList.adapter = adapter
        main_rvList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        //计算按钮被点击
        main_btCaculation.setOnClickListener { calculation() }
        //自动&手动计算切换
        main_btCaculation.setOnLongClickListener {
            val strCalculationMode: String =
                when (main_btCaculation.text) {
                    "计算" -> {
                        isAuto = true; "自动计算"
                    }
                    else -> {
                        isAuto = false; "计算"
                    }
                }
            edit.putBoolean("autoMode", isAuto).apply()
            main_btCaculation.text = strCalculationMode
            true
        }
        //清空所有编辑框
        main_btClear.setOnClickListener {
            for (datum in data) datum.value = 0f

            adapter.notifyDataSetChanged()
            //计算一遍，把总额显示窗口文字改变下
            calculation()
        }
        //计算一遍，把总额显示窗口文字改变下
        calculation()
        //添加数据弹窗
        main_fabAdd.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.name_editor, null)
            val i = (main_father.width * 0.8).toInt()
            val window = PopupWindow(view, i, 180)

            window.isOutsideTouchable = true
            window.isFocusable = true
            window.setBackgroundDrawable(ColorDrawable())

            val etName = view.findViewById(R.id.name_etName) as EditText
            val btAdd = view.findViewById(R.id.name_btAdd) as Button

            //长按不解散窗口
            btAdd.setOnLongClickListener {
                addOnClick(etName, adapter, window)
                true
            }
            //单击要解散窗口
            btAdd.setOnClickListener {
                addOnClick(etName, adapter, window, true)
            }
            //显示弹窗
            window.showAsDropDown(main_fabAdd)
        }
    }

    private fun addOnClick(
        etName: EditText,
        adapter: Adapter,
        window: PopupWindow,
        isDismiss: Boolean = false
    ) {
        val name = etName.text.toString()
        //去重复
        for (datum in data) {
            if (datum.name == name) {
                Toast.makeText(this, "名称不能重复！", Toast.LENGTH_SHORT).show()
                return
            }
        }

        //编辑框不能为空
        if (name.isNotEmpty()) {
            if (name.matches(regex)) hasMinuend = true

            data.add(NameValue(name, 0f))
            adapter.notifyItemInserted(data.size)
            isSave = true
            if (isDismiss) window.dismiss()
        } else Toast.makeText(this, "名称不能为空！", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        if (isSave) {
            isSave = false
            try {
                //没有文件，则创建文件
                if (!pathFile.exists()) pathFile.createNewFile()

                val fileOut = FileOutputStream(pathFile)
                val sb = StringBuffer()
                for (datum in data) sb.append("\n${datum.name}")
                if (sb.isNotEmpty()) sb.deleteCharAt(0)//删除首个换行符
                fileOut.write(sb.toString().toByteArray())
                fileOut.close()
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**加载数据*/
    private fun loadDataTo(data: ArrayList<NameValue>) {
        //有数据就不再重复添加了
        if (data.size > 0) return

        try {
            val fileIn = FileInputStream(pathFile)
            val readBytes = fileIn.readBytes()
            val string = String(readBytes).replace("\r".toRegex(), "")

            val split = string.split("\n")
            for (name in split) {
                if (name.matches(regex)) hasMinuend = true
                if (name.isNotEmpty()) data.add(NameValue(name, 0f))
            }
            fileIn.close()
        } catch (e: FileNotFoundException) {
            Toast.makeText(this, "没有表格数据！", Toast.LENGTH_LONG).show()
        }
    }

    /**请求权限*/
    private fun requestPermission() {
        //要请求的权限
        val strPermissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val listPermissions = LinkedList<String>()

        //检查软件是否有权限
        for (strPermission in strPermissions) {
            val state = checkSelfPermission(strPermission)
            if (state == PackageManager.PERMISSION_DENIED) listPermissions.add(strPermission)
        }

        //没有权限才则请求
        if (listPermissions.isNotEmpty()) {
            val strPermissionTemp = arrayOfNulls<String>(listPermissions.size)
            for (i in listPermissions.indices) strPermissionTemp[i] = listPermissions[i]
            requestPermissions(strPermissionTemp, 1)
        }
    }

    /**权限请求结果反馈*/
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //显示权限请求失败结果
        val iterator = permissions.iterator()
        val denied = StringBuffer()
        for (grantResult in grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) denied.append(iterator.next() + "\n")
        }
        if (denied.isNotEmpty())
            Toast.makeText(this, "权限\"${denied}\"请求失败...", Toast.LENGTH_LONG).show()
    }

    //结构数据类
    data class NameValue(var name: String, var value: Float)

    //适配器
    class Adapter(private val context: Context, private val data: ArrayList<NameValue>) :
        RecyclerView.Adapter<Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val inflate = LayoutInflater
                .from(context)
                .inflate(R.layout.sample_item, parent, false)

            return Holder(inflate)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.run {
                val nv = data[position]

                //设置在列表内显示的数据
                tvTitle.text = nv.name
                etInput.setText(if (nv.value != 0f) nv.value.toString() else "")
                etInput.inputType = InputType.TYPE_NULL
                tvIndex.text = (position + 1).toString()
                tvTitle.typeface = Typeface.DEFAULT
                tvTitle.setTextColor(Color.GRAY)

                //正负号转换
                tvTitle.setOnClickListener {
                    nv.value = -nv.value
                    val strShowValue =
                        if (nv.value == 0f) {
                            if (nv.name.matches(regex)) {
                                nv.name = nv.name.replace("《|》".toRegex(), "")
                            } else nv.name = "《${nv.name}》"

                            //看看有没有被减数
                            checkoutMinuend()
                            notifyItemChanged(position)
                            isSave = true
                            ""
                        } else nv.value.toString()
                    etInput.setText(strShowValue)
                    //自动计算
                    if (isAuto) calculation()
                }
                //删除选项弹出窗
                tvTitle.setOnLongClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("确定删除“${nv.name}”这一项？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定") { _, _ ->
                            data.remove(nv)

                            //看看有没有被减数
                            checkoutMinuend()

                            isSave = true
                            notifyItemRemoved(position)
                            Handler {
                                notifyDataSetChanged()
                                true
                            }.sendEmptyMessageDelayed(0, 500)
                        }.create().show()
                    true
                }
                etInput.setOnLongClickListener {
                    etInput.text.clear()
                    nv.value = 0f
                    //自动计算
                    if (isAuto) calculation()
                    true
                }
                //弹窗输入数字的窗口
                etInput.onFocusChangeListener = View.OnFocusChangeListener { _, isHaveFocus ->
                    val strInput = etInput.text.toString()
                    if (isHaveFocus) {
                        //有焦点
                        toastNumberWindow(etInput, nv)
                        tvTitle.setTextColor(Color.BLACK)
                        tvTitle.typeface = Typeface.DEFAULT_BOLD
                    } else {
                        //无焦点
                        tvTitle.setTextColor(Color.GRAY)
                        tvTitle.typeface = //编辑框有内容设置粗体，否则正常
                            if (strInput.isNotEmpty()) Typeface.DEFAULT_BOLD
                            else Typeface.DEFAULT
                    }
                }
            }
        }

        private fun checkoutMinuend() {
            hasMinuend = false
            for (datum in data) {
                if (datum.name.matches(regex)) {
                    hasMinuend = true;break
                }
            }
        }

        /**弹窗输入数字的窗口*/
        private fun Holder.toastNumberWindow(etInput: EditText, nv: NameValue) {
            val view = LayoutInflater.from(context).inflate(R.layout.number_keyboard, null)
            val rvList = view.findViewById(R.id.keyboard_rcList) as RecyclerView
            val size = (this.etInput.width * 0.7f).toInt()
            val window = PopupWindow(view, size, size)


            window.isOutsideTouchable = true
            window.setBackgroundDrawable(ColorDrawable())
//            window.isFocusable = true

            rvList.adapter = KeyboardAdapter(context, this.etInput, window, tvTitle)
            rvList.layoutManager = GridLayoutManager(context, 4)

            val watcher = object : TextWatcher {
                override fun afterTextChanged(editable: Editable) {
                    val text = editable.toString()

                    //编辑框为空或者非法字符，设置内容为0
                    nv.value =
                        if (text.isNotEmpty()) {
                            try {
                                text.toFloat()
                            } catch (e: Exception) {
                                editable.clear()
                                0f
                            }
                        } else 0F

                    //自动计算
                    if (isAuto) calculation()
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) =
                    Unit

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
            }

            //添加监听
            etInput.addTextChangedListener(watcher)
            //弹窗解散后，移除监听器
            window.setOnDismissListener {
                etInput.removeTextChangedListener(watcher)
                Handler {
                    etInput.clearFocus()
                    true
                }.sendEmptyMessageDelayed(0, 120)
            }
            //显示弹窗
            window.showAsDropDown(this.etInput, 20, -22)
        }
    }

    //虚拟键盘的适配器
    class KeyboardAdapter(
        private val context: Context,
        private val etInput: EditText,
        private val window: PopupWindow,
        private val tvTitle: TextView
    ) :
        RecyclerView.Adapter<KeyboardHolder>() {
        private val number =
            ArrayList<String>(
                arrayListOf(
                    "1", "2", "3", "4",
                    "5", "6", "7", "8",
                    "9", "0", "00", "100",
                    "↓", ".", "←", "空"
                )
            )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyboardHolder {
            val inflate = LayoutInflater
                .from(context)
                .inflate(R.layout.sample_item_text, parent, false)
            inflate.layoutParams.height = (window.height / 4) - 8
            return KeyboardHolder(inflate)
        }

        override fun getItemCount(): Int = number.size

        override fun onBindViewHolder(holder: KeyboardHolder, position: Int) {
            val strNumber = number[position]

            holder.tvTitle.text = strNumber
            holder.tvTitle.textSize = if (strNumber.length > 1) 18f else 24f
            holder.tvTitle.setOnClickListener {
                val editable = etInput.editableText
                val strInput = editable.toString()

                when (strNumber) {
                    "" -> {
                    }
                    //跳转到下一个编辑框
                    "↓" -> {
                        etInput.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                        window.dismiss()
                    }
                    //向左删除
                    "←" -> {
                        val len = strInput.length
                        if (len > 0) editable.delete(len - 1, len)
                    }
                    //情况编辑框字符
                    "空" -> editable.clear()
                    //输入数字
                    else -> {
                        try {
                            (strInput + strNumber).toFloat()//去除异常

                            val isMinuend = tvTitle.text.matches(regex)

                            if (hasMinuend && !isMinuend && editable.isEmpty()) editable.append("-$strNumber")
                            else editable.append(strNumber)
                        } catch (e: Exception) {
                            Toast.makeText(context, "输入不被允许！", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

    }

    //虚拟键盘的持有者
    class KeyboardHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.itemText_text)
    }

    //持有者
    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container = itemView
        val tvTitle: TextView = itemView.findViewById(R.id.item_tvTitle)
        val tvIndex: TextView = itemView.findViewById(R.id.item_tvIndex)
        val etInput: EditText = itemView.findViewById(R.id.item_etContent)
    }
}