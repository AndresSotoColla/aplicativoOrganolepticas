import com.squareup.moshi.Moshi  
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory  
data class Test(val num: Double)  
fun main() {  
  val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()  
  val adapter = moshi.adapter(Test::class.java)  
  println(adapter.toJson(Test(0.0)))  
  println(adapter.toJson(Test(1.0)))  
}  
