/**
 * Copyright (c) 2005, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia development team" nor the names of
 *      its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * An extension of <code>KoLRequest</code> which handles fights
 * and battles.  A new instance is created and started for every
 * round of battle.
 */

public class FightRequest extends KoLRequest
{
	private static final Pattern SKILL_PATTERN = Pattern.compile( "whichskill=(\\d+)" );
	private static final Pattern ITEM1_PATTERN = Pattern.compile( "whichitem=(\\d+)" );
	private static final Pattern ITEM2_PATTERN = Pattern.compile( "whichitem2=(\\d+)" );

	public static final AdventureResult DICTIONARY1 = new AdventureResult( 536, 1 );
	public static final AdventureResult DICTIONARY2 = new AdventureResult( 1316, 1 );

	private int roundCount;
	private boolean shouldRun = true;
	private int offenseModifier = 0, defenseModifier = 0;

	private String action1, action2;
	private MonsterDatabase.Monster monsterData;
	private String encounterLookup = "";

	private static final String [] RARE_MONSTERS =
	{
		// Ultra-rare monsters

		"baiowulf",
		"crazy bastard",
		"hockey elemental",
		"hypnotist of hey deze",
		"infinite meat bug",
		"master of thieves"
	};

	/**
	 * Constructs a new <code>FightRequest</code>.  Theprovided will
	 * be used to determine whether or not the fight should be started and/or
	 * continued, and the user settings will be used to determine the kind
	 * of action1 to be taken during the battle.
	 */

	public FightRequest()
	{
		this( true );
	}

	public FightRequest( boolean isFirstRound )
	{
		super( "fight.php" );
		this.roundCount = isFirstRound ? 0 : -100;

		this.encounter = "";
		this.encounterLookup = "";

		this.monsterData = null;

		this.offenseModifier = 0;
		this.defenseModifier = 0;
	}

	public void nextRound()
	{
		clearDataFields();
		++roundCount;

		// Now, to test if the user should run away from the
		// battle - this is an HP test.

		int haltTolerance = (int)( StaticEntity.getFloatProperty( "battleStop" ) * (float) KoLCharacter.getMaximumHP() );

		if ( passwordHash == null )
		{
			action1 = "attack";
		}
		else
		{
			action1 = CombatSettings.getShortCombatOptionName( StaticEntity.getProperty( "battleAction" ) );
			action2 = null;

			for ( int i = 0; i < RARE_MONSTERS.length; ++i )
				if ( encounterLookup.indexOf( RARE_MONSTERS[i] ) != -1 )
					KoLmafia.updateDisplay( ABORT_STATE, "You have encountered the " + encounter );
		}

		if ( roundCount == 1 )
		{
			// If this is the first round, you
			// actually wind up submitting no
			// extra data.

			action1 = "attack";
		}
		else if ( action1.equals( "custom" ) )
		{
			action1 = CombatSettings.getSetting( encounterLookup, roundCount - 2 );
		}

		if ( action1.startsWith( "consult" ) )
		{
			responseText = StaticEntity.globalStringReplace( responseText, "\"", "\\\"" );
			DEFAULT_SHELL.executeCommand( "call", action1.substring( "consult".length() ).trim() + " (" + roundCount +
				", \"" + encounterLookup + "\", \"" + responseText + "\" )" );

			shouldRun = false;
			return;
		}

		// Let the de-level action figure out what
		// should be done, and then re-process.

		if ( action1.equals( "delevel" ) )
			action1 = getMonsterWeakenAction();

		if ( action1 == null || action1.equals( "abort" ) || !KoLmafia.permitsContinue() )
		{
			// If the user has chosen to abort
			// combat, flag it.

			action1 = null;
		}
		else if ( haltTolerance != 0 && KoLCharacter.getCurrentHP() <= haltTolerance )
		{
			// If you plan on halting the battle
			// due to HP loss, then flag it.

			action1 = null;
		}
		else if ( action1.startsWith( "run" ) )
		{
			action1 = "runaway";
			addFormField( "action", action1 );
		}
		else if ( action1.equals( "attack" ) )
		{
			action1 = "attack";
			if ( roundCount != 1 )
				addFormField( "action", action1 );
		}

		// If the player wants to use an item, make sure he has one
		else if ( action1.startsWith( "item" ) )
		{
			int itemID = StaticEntity.parseInt( action1.substring( 4 ) );
			int itemCount = (new AdventureResult( itemID, 1 )).getCount( inventory );

			if ( itemCount == 0 )
			{
				action1 = "attack";
				addFormField( "action", action1 );
			}
			else
			{
				addFormField( "action", "useitem" );
				addFormField( "whichitem", String.valueOf( itemID ) );

				if ( KoLCharacter.hasSkill( "Ambidextrous Funkslinging" ) && itemCount >= 2 )
				{
					action2 = action1;
					addFormField( "whichitem2", String.valueOf( itemID ) );
				}
			}
		}

		// Skills use MP. Make sure the character has enough
		else if ( KoLCharacter.getCurrentMP() < getActionCost() && passwordHash != null )
		{
			action1 = "attack";
			addFormField( "action", action1 );
		}

		// If the player wants to use a skill, make sure he knows it
		else
		{
			String skillName = ClassSkillsDatabase.getSkillName( StaticEntity.parseInt( action1 ) );

			if ( KoLmafiaCLI.getCombatSkillName( skillName ) == null )
			{
				action1 = "attack";
				addFormField( "action", action1 );
			}
			else
			{
				addFormField( "action", "skill" );
				addFormField( "whichskill", action1 );
			}
		}
	}

	/**
	 * Executes the single round of the fight.  If the user wins or loses,
	 * thewill be notified; otherwise, the next battle will be run
	 * automatically.  All fighting terminates if thecancels their
	 * request; note that battles are not automatically completed.  However,
	 * the battle's execution will be reported in the statistics for the
	 * requests, and will count against any outstanding requests.
	 */

	public void run()
	{
		while ( KoLmafia.permitsContinue() && (responseText == null || responseText.indexOf( "fight.php" ) != -1) )
		{
			clearDataFields();
			action1 = null;
			action2 = null;

			if ( !KoLmafia.refusesContinue() )
				nextRound();

			if ( !shouldRun )
				return;

			super.run();

			if ( KoLmafia.refusesContinue() || action1 == null )
			{
				if ( getAdventuresUsed() == 1 )
				{
					KoLmafia.updateDisplay( ABORT_STATE, "Battle completed." );
					return;
				}
				if ( passwordHash != null )
				{
					showInBrowser( true );
					KoLmafia.updateDisplay( ABORT_STATE, "You're on your own, partner." );
				}
				else
				{
					KoLmafia.updateDisplay( ABORT_STATE, "Please finish your battle in-browser first." );
				}
			}
		}
	}

	private boolean isAcceptable( int offenseModifier, int defenseModifier )
	{
		if ( monsterData == null )
			return true;

		return monsterData.hasAcceptableDodgeRate( this.offenseModifier + offenseModifier ) &&
			!monsterData.willAlwaysMiss( this.defenseModifier + defenseModifier );
	}

	private String getMonsterWeakenAction()
	{
		if ( isAcceptable( 0, 0 ) )
			return "attack";

		int desiredSkill = 0;
		boolean isAcceptable = false;

		// Disco Eye-Poke
		if ( !isAcceptable && KoLCharacter.hasSkill( "Disco Eye-Poke" ) )
		{
			desiredSkill = 5003;
			isAcceptable = isAcceptable( -1, -1 );
		}

		// Disco Dance of Doom
		if ( !isAcceptable && KoLCharacter.hasSkill( "Disco Dance of Doom" ) )
		{
			desiredSkill = 5005;
			isAcceptable = isAcceptable( -3, -3 );
		}

		// Disco Dance II: Electric Boogaloo
		if ( !isAcceptable && KoLCharacter.hasSkill( "Disco Dance II: Electric Boogaloo" ) )
		{
			desiredSkill = 5008;
			isAcceptable = isAcceptable( -5, -5 );
		}

		// Entangling Noodles
		if ( !isAcceptable && KoLCharacter.hasSkill( "Entangling Noodles" ) )
		{
			desiredSkill = 3004;
			isAcceptable = isAcceptable( -6, 0 );
		}

		// Disco Face Stab
		if ( !isAcceptable && KoLCharacter.hasSkill( "Disco Face Stab" ) )
		{
			desiredSkill = 5012;
			isAcceptable = isAcceptable( -7, -7 );
		}

		return desiredSkill == 0 ? null : String.valueOf( desiredSkill );
	}

	protected void processResults()
	{
		// Spend MP and consume items

		payActionCost();

		// If this is the first round, then register the opponent
		// you are fighting against.

		encounterLookup = CombatSettings.encounterKey( encounter );
		monsterData = MonsterDatabase.findMonster( encounter );

		if ( responseText.indexOf( "fight.php" ) == -1 )
		{
			if ( KoLCharacter.getCurrentHP() == 0 )
				KoLmafia.updateDisplay( ERROR_STATE, "You were defeated!" );
		}
	}

	public int getCombatRound()
	{	return roundCount;
	}

	private int getActionCost()
	{
		if ( action1.equals( "attack" ) )
			return 0;

		if ( action1.startsWith( "item" ) )
			return 0;

		return ClassSkillsDatabase.getMPConsumptionByID( StaticEntity.parseInt( action1 ) );
	}

	private boolean hasActionCost( int itemID )
	{
		switch ( itemID )
		{
			case 2:		// Seal Tooth
			case 4:		// Scroll of Turtle Summoning
			case 8:		// Spices
			case 536:	// Dictionary 1
			case 1316:	// Dictionary 2

				return false;

			default:

				return true;
		}
	}

	private void payActionCost()
	{
		if ( action1 == null || action1.equals( "" ) )
			return;

		if ( action1.equals( "attack" ) || action1.equals( "runaway" ) )
			return;

		if ( action1.startsWith( "item" ) )
		{
			int id1 = StaticEntity.parseInt( action1.substring( 4 ) );

			if ( hasActionCost( id1 ) )
				StaticEntity.getClient().processResult( new AdventureResult( id1, -1 ) );

			if ( action2 == null || action2.equals( "" ) )
				return;

			int id2 = StaticEntity.parseInt( action2.substring( 4 ) );

			if ( hasActionCost( id2 ) )
				StaticEntity.getClient().processResult( new AdventureResult( id2, -1 ) );

			return;
		}

		int skillID = StaticEntity.parseInt( action1 );
		int mpCost = ClassSkillsDatabase.getMPConsumptionByID( skillID );

		switch ( skillID )
		{
			case 2005: // Shieldbutt
				offenseModifier -= 5;
				defenseModifier -= 5;
				break;

			case 3004: // Entangling Noodles
				offenseModifier -= 6;
				break;

			case 5003: // Disco Eye-Poke
				offenseModifier -= 1;
				defenseModifier -= 1;
				break;

			case 5005: // Disco Dance of Doom
				offenseModifier -= 3;
				defenseModifier -= 3;
				break;

			case 5008: // Disco Dance II: Electric Boogaloo
				offenseModifier -= 5;
				defenseModifier -= 5;
				break;

			case 5012: // Disco Face Stab
				offenseModifier -= 7;
				defenseModifier -= 7;
				break;
		}

		if ( mpCost > 0 )
			StaticEntity.getClient().processResult( new AdventureResult( AdventureResult.MP, 0 - mpCost ) );
	}

	/**
	 * An alternative method to doing adventure calculation is determining
	 * how many adventures are used by the given request, and subtract
	 * them after the request is done.  This number defaults to <code>zero</code>;
	 * overriding classes should change this value to the appropriate
	 * amount.
	 *
	 * @return	The number of adventures used by this request.
	 */

	public int getAdventuresUsed()
	{	return responseText == null ? 0 : responseText.indexOf( "fight.php" ) == -1 ? 1 : 0;
	}

	public static boolean processRequest( String urlString )
	{
		if ( urlString.indexOf( "fight.php?" ) == -1 )
			return urlString.indexOf( "fight.php" ) != -1;

		Matcher skillMatcher = SKILL_PATTERN.matcher( urlString );
		if ( skillMatcher.find() )
		{
			String skill = ClassSkillsDatabase.getSkillName( StaticEntity.parseInt( skillMatcher.group(1) ) );
			if ( skill == null )
				KoLmafia.getSessionStream().println( KoLCharacter.getUsername() + " casts the enchanted spell of CHANCE!" );
			else
				KoLmafia.getSessionStream().println( KoLCharacter.getUsername() + " casts the enchanted spell of " + skill.toUpperCase() + "!" );

			return true;
		}

		Matcher itemMatcher = ITEM1_PATTERN.matcher( urlString );
		if ( itemMatcher.find() )
		{
			String item = TradeableItemDatabase.getItemName( StaticEntity.parseInt( itemMatcher.group(1) ) );
			if ( item == null )
				KoLmafia.getSessionStream().print( KoLCharacter.getUsername() + " plays Garin's Harp" );
			else
				KoLmafia.getSessionStream().print( KoLCharacter.getUsername() + " uses the " + item );

			itemMatcher = ITEM2_PATTERN.matcher( urlString );
			if ( itemMatcher.find() )
			{
				KoLmafia.getSessionStream().print( " and " );

				item = TradeableItemDatabase.getItemName( StaticEntity.parseInt( itemMatcher.group(1) ) );
				if ( item == null )
					KoLmafia.getSessionStream().print( "plays the Fairy Flute" );
				else
					KoLmafia.getSessionStream().print( "uses the " + item );
			}

			KoLmafia.getSessionStream().println( "!" );
			return true;
		}

		if ( urlString.indexOf( "runaway" ) != -1 )
		{
			KoLmafia.getSessionStream().println( KoLCharacter.getUsername() + " casts the spell of RETURN!" );
		}
		else
		{
			KoLmafia.getSessionStream().println( KoLCharacter.getUsername() + " attacks with " +
				"fear-inducing body language!" );
		}

		return true;
	}

}
