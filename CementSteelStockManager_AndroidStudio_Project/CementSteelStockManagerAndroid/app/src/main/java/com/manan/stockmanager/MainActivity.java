package com.manan.stockmanager;

import android.app.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private DB db;
    private LinearLayout root, content, nav;
    private int page = 0;
    private final String[] cementTypes = {"OPC", "PPC", "SUPER", "WEATHER PLUS"};
    private final int ink = Color.rgb(28, 43, 61);
    private final int blue = Color.rgb(34, 96, 170);
    private final int pale = Color.rgb(239, 245, 251);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new DB(this);
        render();
    }

    private void render() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,249,252));
        setContentView(root);
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(14)); header.setBackgroundColor(ink);
        TextView title = text("Cement & Steel Stock", 22, Color.WHITE, true);
        TextView subtitle = text("Offline inventory • saved on this phone", 13, Color.rgb(210,223,239), false);
        header.addView(title); header.addView(subtitle); root.addView(header);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(14),dp(14),dp(18)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        nav = new LinearLayout(this); nav.setPadding(dp(5),dp(5),dp(5),dp(5)); nav.setBackgroundColor(Color.WHITE);
        String[] tabs = {"Cement", "Steel", "Reports"};
        for (int i=0;i<tabs.length;i++) {
            final int p=i; Button b=button(tabs[i], i==page?blue:ink);
            b.setTextColor(Color.WHITE); nav.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
            b.setOnClickListener(v->{page=p;render();});
        }
        root.addView(nav);
        if(page==0) cementPage(); else if(page==1) steelPage(); else reportsPage();
    }

    private void cementPage() {
        heading("Cement inventory");
        TextView note=text("Stock is counted in bags. No payment tracking.",13,Color.DKGRAY,false); content.addView(note);
        for(String type:cementTypes) {
            double stock=db.cementStock(type);
            card(type, String.format(Locale.US,"%.0f bags available",stock), "Opening + received − sales");
        }
        section("Record a movement");
        Button opening=button("Add opening stock",blue), received=button("Receive stock",blue), sale=button("Record sale",ink);
        content.addView(opening); content.addView(received); content.addView(sale);
        opening.setOnClickListener(v->cementMovement("OPENING"));
        received.setOnClickListener(v->cementMovement("RECEIVED"));
        sale.setOnClickListener(v->cementMovement("SALE"));
        section("Recent cement movements");
        Cursor c=db.getReadableDatabase().rawQuery("SELECT type,kind,qty,date FROM cement_moves ORDER BY id DESC LIMIT 30",null);
        if(!c.moveToFirst()) empty("No cement movements recorded yet.");
        else do { row(c.getString(0)+" • "+c.getString(1), fmt(c.getDouble(2))+" bags", c.getString(3)); } while(c.moveToNext());
        c.close();
    }

    private void cementMovement(String kind) {
        LinearLayout form=form();
        Spinner types=spinner(cementTypes); form.addView(label("Cement type")); form.addView(types);
        EditText qty=input("Quantity in bags (whole number)"); form.addView(label("Quantity (bags)")); form.addView(qty);
        EditText date=input(today()); form.addView(label("Date (YYYY-MM-DD)")); form.addView(date);
        new AlertDialog.Builder(this).setTitle(cementTitle(kind)).setView(form)
            .setNegativeButton("Cancel",null).setPositiveButton("Save", (d,w)->{
                String type=types.getSelectedItem().toString(); double q=parse(qty.getText().toString());
                String dt=date.getText().toString().trim();
                if(q<=0 || q!=Math.floor(q)){toast("Enter a positive whole number of bags.");return;}
                if(kind.equals("SALE") && q>db.cementStock(type)){toast("Sale exceeds available stock.");return;}
                if(!validDate(dt)){toast("Use date format YYYY-MM-DD.");return;}
                db.getWritableDatabase().execSQL("INSERT INTO cement_moves(type,kind,qty,date) VALUES(?,?,?,?)",new Object[]{type,kind,q,dt});
                toast("Cement movement saved."); render();
            }).show();
    }
    private String cementTitle(String k){return k.equals("OPENING")?"Opening stock":k.equals("RECEIVED")?"Receive cement":"Record cement sale";}

    private void steelPage() {
        heading("Steel inventory");
        double physical=db.steelQty(), reserved=db.customerPending(), available=physical-reserved, avg=db.steelAvgCost();
        card("Physical stock", qtyText(physical), "Weighted average cost: "+money(avg)+"/t");
        card("Reserved for customers",qtyText(reserved),"Pending customer deliveries");
        card("Available to book",qtyText(Math.max(0,available)),"Physical stock minus customer reservations");
        card("Pending company bookings",qtyText(db.companyPending()),"Not yet dispatched into storage");
        section("Company side");
        Button cb=button("＋ Company booking",blue), cd=button("↓ Company dispatch / receive",blue);
        content.addView(cb); content.addView(cd);
        cb.setOnClickListener(v->companyBooking()); cd.setOnClickListener(v->companyDispatch());
        section("Customer side");
        Button ub=button("＋ Customer booking",ink), ud=button("↓ Customer delivery",ink);
        content.addView(ub); content.addView(ud);
        ub.setOnClickListener(v->customerBooking()); ud.setOnClickListener(v->customerDelivery());
        section("Pending company bookings");
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,company,qty,dispatched,rate,date FROM company_bookings WHERE qty>dispatched ORDER BY id DESC",null);
        if(!c.moveToFirst()) empty("No pending company bookings.");
        else do {double pending=c.getDouble(2)-c.getDouble(3);row(c.getString(1),qtyText(pending)+" • "+money(c.getDouble(4))+"/t","Booked "+c.getString(5));}while(c.moveToNext());
        c.close();
        section("Pending customer bookings");
        Cursor u=db.getReadableDatabase().rawQuery("SELECT customer,qty,delivered,rate,date FROM customer_bookings WHERE qty>delivered ORDER BY id DESC",null);
        if(!u.moveToFirst()) empty("No pending customer bookings.");
        else do {row(u.getString(0),qtyText(u.getDouble(1)-u.getDouble(2))+" • "+money(u.getDouble(3))+"/t","Booked "+u.getString(4));}while(u.moveToNext());
        u.close();
    }

    private void companyBooking() {
        LinearLayout f=form(); EditText company=input("Company name"), qty=input("Quantity in tons (e.g. 2.500)"), rate=input("Booking rate ₹ per ton"), date=input(today());
        addField(f,"Company name",company);addField(f,"Quantity (tons)",qty);addField(f,"Rate per ton (₹)",rate);addField(f,"Booking date",date);
        new AlertDialog.Builder(this).setTitle("Company booking").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String n=company.getText().toString().trim(), dt=date.getText().toString().trim();double q=parse(qty.getText().toString()),r=parse(rate.getText().toString());
            if(n.isEmpty()||q<=0||r<=0||!validDate(dt)){toast("Enter company, positive quantity/rate, and valid date.");return;}
            db.getWritableDatabase().execSQL("INSERT INTO company_bookings(company,qty,dispatched,rate,date) VALUES(?,?,0,?,?)",new Object[]{n,q,r,dt});
            toast("Company booking saved.");render();
        }).show();
    }

    private void companyDispatch() {
        ArrayList<Integer> ids=new ArrayList<>(); ArrayList<String> labels=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,company,qty,dispatched,rate FROM company_bookings WHERE qty>dispatched ORDER BY id DESC",null);
        while(c.moveToNext()){ids.add(c.getInt(0));labels.add(c.getString(1)+" • pending "+qtyText(c.getDouble(2)-c.getDouble(3))+" @ "+money(c.getDouble(4))+"/t");}c.close();
        if(ids.isEmpty()){toast("No pending company booking to dispatch.");return;}
        LinearLayout f=form(); Spinner pick=spinner(labels.toArray(new String[0]));EditText qty=input("Dispatch quantity in tons");EditText date=input(today());
        addField(f,"Company booking",pick);addField(f,"Dispatch quantity (tons)",qty);addField(f,"Dispatch date",date);
        new AlertDialog.Builder(this).setTitle("Company dispatch / receive").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            int id=ids.get(pick.getSelectedItemPosition());double q=parse(qty.getText().toString());String dt=date.getText().toString().trim();
            Cursor b=db.getReadableDatabase().rawQuery("SELECT qty,dispatched,rate FROM company_bookings WHERE id=?",new String[]{""+id});
            if(!b.moveToFirst()){b.close();toast("Booking not found.");return;}double pending=b.getDouble(0)-b.getDouble(1),r=b.getDouble(2);b.close();
            if(q<=0||q>pending+0.000001||!validDate(dt)){toast("Quantity must be positive and no more than pending booking.");return;}
            SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();
            try{
                s.execSQL("UPDATE company_bookings SET dispatched=dispatched+? WHERE id=?",new Object[]{q,id});
                double oldQ=db.steelQty(),oldV=db.steelValue();
                s.execSQL("INSERT INTO steel_moves(kind,qty,unit_cost,date,ref) VALUES('RECEIPT',?,?,?,?)",new Object[]{q,r,dt,"Company booking #"+id});
                s.setTransactionSuccessful();
            }finally{s.endTransaction();}
            toast("Dispatch received into physical stock.");render();
        }).show();
    }

    private void customerBooking() {
        LinearLayout f=form();EditText customer=input("Customer name"),qty=input("Quantity in tons (e.g. 0.750)"),rate=input("Selling rate ₹ per ton"),date=input(today());
        addField(f,"Customer name",customer);addField(f,"Quantity (tons)",qty);addField(f,"Selling rate (₹/ton)",rate);addField(f,"Booking date",date);
        new AlertDialog.Builder(this).setTitle("Customer booking").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String n=customer.getText().toString().trim(),dt=date.getText().toString().trim();double q=parse(qty.getText().toString()),r=parse(rate.getText().toString());
            if(n.isEmpty()||q<=0||r<=0||!validDate(dt)){toast("Enter customer, positive quantity/rate, and valid date.");return;}
            double available=db.steelQty()-db.customerPending();
            if(q>available+0.000001){toast("Not enough available physical stock. Available: "+qtyText(Math.max(0,available)));return;}
            double cost=db.steelAvgCost(), profit=(r-cost)*q;
            db.getWritableDatabase().execSQL("INSERT INTO customer_bookings(customer,qty,delivered,rate,cost_at_booking,expected_profit,date) VALUES(?,?,0,?,?,?,?)",new Object[]{n,q,r,cost,profit,dt});
            toast("Customer booking saved. Expected gross profit: "+money(profit));render();
        }).show();
    }

    private void customerDelivery() {
        ArrayList<Integer> ids=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,customer,qty,delivered,rate FROM customer_bookings WHERE qty>delivered ORDER BY id DESC",null);
        while(c.moveToNext()){ids.add(c.getInt(0));labels.add(c.getString(1)+" • pending "+qtyText(c.getDouble(2)-c.getDouble(3))+" @ "+money(c.getDouble(4))+"/t");}c.close();
        if(ids.isEmpty()){toast("No pending customer booking.");return;}
        LinearLayout f=form();Spinner pick=spinner(labels.toArray(new String[0]));EditText qty=input("Delivery quantity in tons"),date=input(today());
        addField(f,"Customer booking",pick);addField(f,"Delivery quantity (tons)",qty);addField(f,"Delivery date",date);
        new AlertDialog.Builder(this).setTitle("Customer delivery").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            int id=ids.get(pick.getSelectedItemPosition());double q=parse(qty.getText().toString());String dt=date.getText().toString().trim();
            Cursor b=db.getReadableDatabase().rawQuery("SELECT qty,delivered,rate FROM customer_bookings WHERE id=?",new String[]{""+id});
            if(!b.moveToFirst()){b.close();toast("Booking not found.");return;}double pending=b.getDouble(0)-b.getDouble(1),r=b.getDouble(2);b.close();
            if(q<=0||q>pending+0.000001||q>db.steelQty()+0.000001||!validDate(dt)){toast("Check quantity, stock, and date.");return;}
            SQLiteDatabase s=db.getWritableDatabase();s.beginTransaction();
            try{
                double cost=db.steelAvgCost();
                s.execSQL("UPDATE customer_bookings SET delivered=delivered+? WHERE id=?",new Object[]{q,id});
                s.execSQL("INSERT INTO steel_moves(kind,qty,unit_cost,date,ref) VALUES('DELIVERY',?,?,?,?)",new Object[]{q,cost,dt,"Customer booking #"+id});
                s.setTransactionSuccessful();
            }finally{s.endTransaction();}
            toast("Delivery recorded. Estimated gross profit on this delivery: "+money((r-cost)*q));render();
        }).show();
    }

    private void reportsPage() {
        heading("Reports");
        section("Cement closing stock");
        for(String t:cementTypes) row(t,fmt(db.cementStock(t))+" bags","");
        section("Steel summary");
        row("Physical stock",qtyText(db.steelQty()),"Average cost "+money(db.steelAvgCost())+"/t");
        row("Reserved for customers",qtyText(db.customerPending()),"Available "+qtyText(Math.max(0,db.steelQty()-db.customerPending())));
        row("Pending company bookings",qtyText(db.companyPending()),"Separate from physical stock");
        section("Customer booking report • expected profit");
        Cursor c=db.getReadableDatabase().rawQuery("SELECT customer,qty,delivered,rate,expected_profit,date FROM customer_bookings ORDER BY id DESC LIMIT 100",null);
        if(!c.moveToFirst()) empty("No customer bookings yet.");
        else do {
            double remaining=c.getDouble(1)-c.getDouble(2);
            row(c.getString(0), "Booked "+qtyText(c.getDouble(1))+" • delivered "+qtyText(c.getDouble(2)), "Expected profit "+money(c.getDouble(4))+" • "+c.getString(5));
        }while(c.moveToNext()); c.close();
        section("Recent steel movements");
        Cursor m=db.getReadableDatabase().rawQuery("SELECT kind,qty,unit_cost,date,ref FROM steel_moves ORDER BY id DESC LIMIT 100",null);
        if(!m.moveToFirst()) empty("No steel dispatches or deliveries recorded yet.");
        else do {row(m.getString(0)+" • "+m.getString(4),qtyText(m.getDouble(1)),"Rate/cost "+money(m.getDouble(2))+"/t • "+m.getString(3));}while(m.moveToNext());m.close();
        section("Data");
        Button info=button("About offline storage",ink);content.addView(info);
        info.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Your data").setMessage("Records are saved in this app's local database on this phone. They do not sync to another phone. Uninstalling the app or clearing its storage can delete the records.").setPositiveButton("OK",null).show());
    }

    private class DB extends SQLiteOpenHelper {
        DB(Context c){super(c,"stock_manager.db",null,1);}
        public void onCreate(SQLiteDatabase s){
            s.execSQL("CREATE TABLE cement_moves(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT,kind TEXT,qty REAL,date TEXT)");
            s.execSQL("CREATE TABLE company_bookings(id INTEGER PRIMARY KEY AUTOINCREMENT,company TEXT,qty REAL,dispatched REAL,rate REAL,date TEXT)");
            s.execSQL("CREATE TABLE customer_bookings(id INTEGER PRIMARY KEY AUTOINCREMENT,customer TEXT,qty REAL,delivered REAL,rate REAL,cost_at_booking REAL,expected_profit REAL,date TEXT)");
            s.execSQL("CREATE TABLE steel_moves(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT,qty REAL,unit_cost REAL,date TEXT,ref TEXT)");
        }
        public void onUpgrade(SQLiteDatabase s,int oldV,int newV){}
        double cementStock(String type){
            Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='SALE' THEN -qty ELSE qty END),0) FROM cement_moves WHERE type=?",new String[]{type});
            double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;
        }
        double steelQty(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='RECEIPT' THEN qty ELSE -qty END),0) FROM steel_moves",null);double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double steelValue(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='RECEIPT' THEN qty*unit_cost ELSE -qty*unit_cost END),0) FROM steel_moves",null);double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double steelAvgCost(){double q=steelQty();return q>0?Math.max(0,steelValue()/q):0;}
        double companyPending(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(qty-dispatched),0) FROM company_bookings",null);double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
        double customerPending(){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(qty-delivered),0) FROM customer_bookings",null);double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
    }

    private void heading(String s){TextView t=text(s,21,ink,true);t.setPadding(0,0,0,dp(8));content.addView(t);}
    private void section(String s){TextView t=text(s,16,ink,true);t.setPadding(0,dp(17),0,dp(7));content.addView(t);}
    private void card(String title,String value,String detail){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(12),dp(14),dp(12));box.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);box.setLayoutParams(lp);
        box.addView(text(title,14,Color.DKGRAY,true));box.addView(text(value,20,ink,true));box.addView(text(detail,12,Color.GRAY,false));content.addView(box);
    }
    private void row(String title,String value,String detail){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(10),dp(12),dp(10));box.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(5);box.setLayoutParams(lp);
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
        TextView a=text(title,14,ink,true),b=text(value,14,blue,true);line.addView(a,new LinearLayout.LayoutParams(0,-2,1));line.addView(b);
        box.addView(line);if(detail!=null&&!detail.isEmpty())box.addView(text(detail,12,Color.GRAY,false));content.addView(box);
    }
    private void empty(String s){TextView t=text(s,13,Color.GRAY,false);t.setPadding(dp(5),dp(8),dp(5),dp(8));content.addView(t);}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(7);b.setLayoutParams(lp);return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout form(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(18),dp(6),dp(18),0);return f;}
    private EditText input(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setHint(hint);return e;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);return s;}
    private TextView label(String s){TextView t=text(s,13,ink,true);t.setPadding(0,dp(7),0,0);return t;}
    private void addField(LinearLayout f,String name,View v){f.addView(label(name));f.addView(v);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private double parse(String s){try{return Double.parseDouble(s.trim().replace(",",""));}catch(Exception e){return -1;}}
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    private boolean validDate(String s){return s.matches("\\d{4}-\\d{2}-\\d{2}");}
    private String money(double n){return "₹"+String.format(Locale.US,"%,.2f",n);}
    private String fmt(double n){return String.format(Locale.US,"%,.0f",n);}
    private String qtyText(double t){return String.format(Locale.US,"%.3f t  (%s kg)",t,fmt(t*1000));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
