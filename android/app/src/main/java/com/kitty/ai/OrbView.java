package com.kitty.ai;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class OrbView extends View {
    private final Paint paint=new Paint(3);private boolean active;
    private final RectF oval=new RectF();
    private final Path cat=new Path();
    private RadialGradient glow;
    public OrbView(Context c){super(c);setContentDescription("KITTY voice interface");}
    public void active(boolean value){active=value;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        float x=w/2f,y=h/2f,r=Math.min(x,y)*.63f;
        if(r>0)glow=new RadialGradient(x,y,r*1.7f,new int[]{0x555C4E9B,0x182B2641,0x000C0D12},null,Shader.TileMode.CLAMP);
        cat.reset();cat.moveTo(x-r*.38f,y-r*.25f);cat.lineTo(x-r*.37f,y+r*.25f);cat.lineTo(x,y+r*.49f);cat.lineTo(x+r*.37f,y+r*.25f);cat.lineTo(x+r*.38f,y-r*.25f);cat.lineTo(x+r*.16f,y-r*.05f);cat.lineTo(x-r*.16f,y-r*.05f);cat.close();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.63f;
        float phase=(System.currentTimeMillis()%6000)/6000f;
        paint.setShader(glow);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r*1.7f,paint);paint.setShader(null);
        for(int i=0;i<4;i++){
            float radius=r*(.80f+i*.09f)+(active?(float)Math.sin((phase*2+i)*Math.PI)*3:0);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(i==1?2.6f:1);paint.setColor(i==1?0xFFCCBEFF:0x555E5872);
            oval.set(x-radius,y-radius,x+radius,y+radius);c.save();c.rotate(phase*360*(i%2==0?1:-1)+i*40,x,y);c.drawArc(oval,15,290-i*15,false,paint);c.restore();
        }
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFE9E2FF);
        c.drawPath(cat,paint);
        paint.setColor(0xFF16131D);paint.setStrokeWidth(3);c.drawLine(x-r*.25f,y+r*.13f,x-r*.07f,y+r*.19f,paint);c.drawLine(x+r*.25f,y+r*.13f,x+r*.07f,y+r*.19f,paint);
        if(isShown())postInvalidateDelayed(active?33:90);
    }
}
