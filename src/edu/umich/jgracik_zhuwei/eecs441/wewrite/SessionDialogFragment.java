package edu.umich.jgracik_zhuwei.eecs441.wewrite;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
//import edu.umich.jgracik_zhuwei.eecs441.wewrite.R;

/*
 * DialogFragment code based off of Android SDK documentation tutorial code:
 * http://developer.android.com/guide/topics/ui/dialogs.html
 */

public class SessionDialogFragment extends DialogFragment
{
  
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) 
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    LayoutInflater inflater = getActivity().getLayoutInflater();
    
    // show the new_file_dialog view (edittext field)
    final View v = inflater.inflate(R.layout.enter_text_dialog, null);
    builder.setView(v)
      .setTitle("Enter session id")
      // create action buttons
      .setPositiveButton(R.string.button_create, new DialogInterface.OnClickListener() // create
      {
        
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
          // get user-entered filename from dialog text field
          EditText id_field = (EditText) v.findViewById(R.id.enter_text_dialog_field);
          long sessionId = 0;
          
          Intent joinSessionIntent = new Intent(getActivity(), TextEditorActivity.class);
          
          try {
            sessionId = Long.parseLong(id_field.getText().toString());
            joinSessionIntent.putExtra(MainActivity.IS_JOIN, true);
            joinSessionIntent.putExtra(MainActivity.SESSION_ID, sessionId);
          } catch (NumberFormatException e) {
            Toast.makeText(getActivity(), "Session id must be numeric only", Toast.LENGTH_LONG).show();
          }
          
          startActivity(joinSessionIntent);
        }
      })
      .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() // cancel
      {
        
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
          SessionDialogFragment.this.getDialog().cancel();
        }
      });
    
    return builder.create();
  }
}
