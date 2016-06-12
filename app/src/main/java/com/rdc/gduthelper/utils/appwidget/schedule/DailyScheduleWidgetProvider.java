package com.rdc.gduthelper.utils.appwidget.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.rdc.gduthelper.R;
import com.rdc.gduthelper.bean.Lesson;
import com.rdc.gduthelper.bean.LessonTACR;
import com.rdc.gduthelper.utils.LessonUtils;
import com.rdc.gduthelper.utils.appwidget.WidgetConfigProvider;
import com.rdc.gduthelper.utils.database.ScheduleDBHelper;
import com.rdc.gduthelper.utils.settings.ScheduleConfig;
import com.rdc.gduthelper.utils.settings.Settings;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Created by seasonyuu on 16-6-6.
 */

public class DailyScheduleWidgetProvider extends AppWidgetProvider {
	private static final String TAG = DailyScheduleWidgetProvider.class.getSimpleName();

	public static final String ACTION_PRE = "com.rdc.gduthelper.CLICK_ACTION_PRE";
	public static final String ACTION_RESTORE = "com.rdc.gduthelper.CLICK_ACTION_RESTORE";
	public static final String ACTION_NEXT = "com.rdc.gduthelper.CLICK_ACTION_NEXT";

	@Override
	public void onEnabled(Context context) {
		super.onEnabled(context);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		Uri uri = Uri.parse(WidgetConfigProvider.SCHEDULE_CONFIG_CONTENT_URI);
		String user = context.getSharedPreferences(
				context.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS)
				.getString(Settings.REMEMBER_USER_DATA_KEY, null);
		if (user == null) {
			Log.e(TAG, "user = null");
			return;
		}
		Cursor cursor = context.getContentResolver()
				.query(uri, null, "id = ?", new String[]{user.split(";")[0]}, null);
		ScheduleConfig config = null;

		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			config = new ScheduleConfig();
			config.setId(cursor.getString(0));
			config.setTerm(cursor.getString(1));
			config.setFirstWeek(cursor.getString(2));
			config.setCardColors(cursor.getString(3));
		}
		cursor.close();
		if (config == null) {
			Log.e(TAG, "config is null while querying Uri(" + uri.toString() + ")");
			return;
		}

		for (int appWidgetId : appWidgetIds) {
			initWidget(context, appWidgetManager, appWidgetId, config);
			addNotify(context, appWidgetId);
		}

	}

	private void initWidget(Context context, AppWidgetManager appWidgetManager,
	                        int appwidgetId, ScheduleConfig config) {
		final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

		ScheduleDBHelper helper = new ScheduleDBHelper(context);
		ArrayList<Lesson> lessons = helper.getLessonList(config.getTerm(), config.getId());
		String[] weekdays = context.getResources().getStringArray(R.array.weekdays);

		Calendar firstWeek = Calendar.getInstance();
		try {
			firstWeek.setTime(format.parse(config.getFirstWeek()));
		} catch (ParseException e) {
			e.printStackTrace();
		}

		Calendar today = Calendar.getInstance();
		Uri uri = Uri.parse(WidgetConfigProvider.WIDGET_CONFIG_CONTENT_URI);
		Cursor cursor = context.getContentResolver()
				.query(uri, null, "widget_id = ?", new String[]{appwidgetId + ""}, null);
		if (cursor == null) {
			Log.e(TAG, "Cursor is null while querying Uri(" + uri.toString() + ")");
			return;
		}
		if (cursor.getCount() == 0) {
			ContentValues contentValues = new ContentValues();
			contentValues.put("widget_id", appwidgetId);
			contentValues.put("calendar", format.format(today.getTime()));
			context.getContentResolver().insert(uri, contentValues);
		} else {
			cursor.moveToFirst();
			String calendar = cursor.getString(1);
			Date configDate = null;
			try {
				configDate = format.parse(calendar);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			if (configDate != null)
				today.setTime(configDate);
		}
		cursor.close();

		TreeMap<LessonTACR, Lesson> todaysLessons = LessonUtils
				.calculateTodaysLessons(firstWeek, today, lessons);

		Bundle data = new Bundle();
		data.putSerializable("todaysLessons", todaysLessons);

		RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
				R.layout.widget_schedule_daily);

		remoteViews.setTextViewText(R.id.widget_schedule_week, "第" +
				LessonUtils.calculateCurrentWeek(config.getFirstWeek(), today) + "周");
		remoteViews.setTextViewText(R.id.widget_schedule_weekday,
				weekdays[today.get(Calendar.DAY_OF_WEEK) - 1]);

		Log.e(TAG, format.format(today.getTime()) + " " + todaysLessons.size());

		remoteViews.setOnClickPendingIntent(R.id.widget_schedule_pre,
				createClickIntent(context, appwidgetId, 1));
		remoteViews.setOnClickPendingIntent(R.id.widget_schedule_restore,
				createClickIntent(context, appwidgetId, 0));
		remoteViews.setOnClickPendingIntent(R.id.widget_schedule_next,
				createClickIntent(context, appwidgetId, 2));

		remoteViews.setEmptyView(R.id.widget_schedule_daily_list, R.id.widget_schedule_daily_empty_view);

		Intent adapter = new Intent(context, DailyScheduleWidgetService.class);
		adapter.putExtra("data", data);
		adapter.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appwidgetId);
		adapter.setData(Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME)));

		remoteViews.setRemoteAdapter(R.id.widget_schedule_daily_list, adapter);

		appWidgetManager.updateAppWidget(appwidgetId, remoteViews);
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
	}

	private PendingIntent createClickIntent(Context context, int appWidgetId, int selection) {
		Intent click = new Intent();
		click.putExtra("widget_id", appWidgetId);
		switch (selection) {
			case 0:
				click.setAction(ACTION_RESTORE);
				break;
			case 1:
				click.setAction(ACTION_PRE);
				break;
			case 2:
				click.setAction(ACTION_NEXT);
				break;
		}
		return PendingIntent.getBroadcast(context, 0, click, 0);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);

		boolean flag = false;
		int calculate = 0;
		switch (intent.getAction()) {
			case ACTION_PRE:
				flag = true;
				calculate = -1;
				break;
			case ACTION_NEXT:
				flag = true;
				calculate = 1;
				break;
			case ACTION_RESTORE:
				flag = true;
				calculate = 0;
				break;
		}
		if (flag) {
			Uri uri = Uri.parse(WidgetConfigProvider.WIDGET_CONFIG_CONTENT_URI);
			final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
			int appWidgetId = intent.getIntExtra("widget_id", -1);
			if (appWidgetId == -1)
				return;

			if (calculate == 0) {
				ContentValues contentValues = new ContentValues();
				contentValues.put("calendar", format.format(Calendar.getInstance().getTime()));

				context.getContentResolver().update(uri, contentValues,
						"widget_id = ?", new String[]{appWidgetId + ""});
			} else {
				Cursor cursor = context.getContentResolver().query(uri, null, "widget_id = ?",
						new String[]{appWidgetId + ""}, null);
				if (cursor == null)
					return;
				else if (cursor.getCount() != 0) {
					cursor.moveToFirst();
					Calendar target = Calendar.getInstance();
					Date configDate = null;
					try {
						configDate = format.parse(cursor.getString(1));
					} catch (ParseException e) {
						e.printStackTrace();
					}
					if (configDate != null) {
						target.setTime(configDate);
					}

					target.add(Calendar.DAY_OF_YEAR, calculate);

					Log.e(TAG, "update " + format.format(target.getTime()));
					ContentValues contentValues = new ContentValues();
					contentValues.put("calendar", format.format(target.getTime()));

					context.getContentResolver().update(uri, contentValues,
							"widget_id = ?", new String[]{appWidgetId + ""});
				}
				cursor.close();
			}

			AppWidgetManager.getInstance(context)
					.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_schedule_daily_list);

			updateTopTitle(context, appWidgetId);
		}
	}

	private void addNotify(Context context, int appWidgetId) {
		Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		intent.setClass(context.getApplicationContext(), DailyScheduleWidgetProvider.class);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		Bundle bundle = new Bundle();
		bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
		intent.putExtras(bundle);

		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.add(Calendar.DAY_OF_YEAR, 1);

		alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
	}

	private void updateTopTitle(Context context, int appwidgetId) {
		Uri uri = Uri.parse(WidgetConfigProvider.SCHEDULE_CONFIG_CONTENT_URI);
		String user = context.getSharedPreferences(
				context.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS)
				.getString(Settings.REMEMBER_USER_DATA_KEY, null);
		if (user == null) {
			Log.e(TAG, "user = null");
			return;
		}
		Cursor cursor = context.getContentResolver()
				.query(uri, null, "id = ?", new String[]{user.split(";")[0]}, null);
		ScheduleConfig config = null;

		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			config = new ScheduleConfig();
			config.setId(cursor.getString(0));
			config.setTerm(cursor.getString(1));
			config.setFirstWeek(cursor.getString(2));
			config.setCardColors(cursor.getString(3));
		}
		cursor.close();
		if (config == null) {
			Log.e(TAG, "config is null while querying Uri(" + uri.toString() + ")");
			return;
		}

		final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

		final String[] weekdays = context.getResources().getStringArray(R.array.weekdays);

		Calendar firstWeek = Calendar.getInstance();
		try {
			firstWeek.setTime(format.parse(config.getFirstWeek()));
		} catch (ParseException e) {
			e.printStackTrace();
		}

		Calendar today = Calendar.getInstance();
		Uri configUri = Uri.parse(WidgetConfigProvider.WIDGET_CONFIG_CONTENT_URI);
		Cursor configCursor = context.getContentResolver()
				.query(configUri, null, "widget_id = ?", new String[]{appwidgetId + ""}, null);
		if (configCursor == null) {
			Log.e(TAG, "Cursor is null while querying Uri(" + uri.toString() + ")");
			return;
		}
		if (configCursor.getCount() == 0) {
			ContentValues contentValues = new ContentValues();
			contentValues.put("widget_id", appwidgetId);
			contentValues.put("calendar", format.format(today.getTime()));
			context.getContentResolver().insert(uri, contentValues);
		} else {
			configCursor.moveToFirst();
			String calendar = configCursor.getString(1);
			Date configDate = null;
			try {
				configDate = format.parse(calendar);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			if (configDate != null)
				today.setTime(configDate);
		}
		configCursor.close();

		RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
				R.layout.widget_schedule_daily);

		remoteViews.setTextViewText(R.id.widget_schedule_week, "第" +
				LessonUtils.calculateCurrentWeek(config.getFirstWeek(), today) + "周");
		remoteViews.setTextViewText(R.id.widget_schedule_weekday,
				weekdays[today.get(Calendar.DAY_OF_WEEK) - 1]);


		remoteViews.setOnClickPendingIntent(R.id.widget_schedule_pre,
				createClickIntent(context, appwidgetId, 1));
		remoteViews.setOnClickPendingIntent(R.id.widget_schedule_restore,
				createClickIntent(context, appwidgetId, 0));
		remoteViews.setOnClickPendingIntent(R.id.widget_schedule_next,
				createClickIntent(context, appwidgetId, 2));

		AppWidgetManager.getInstance(context).updateAppWidget(appwidgetId, remoteViews);
	}
}
